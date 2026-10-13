"""Regression tests: local Git remotes are real; GitHub calls never use the network."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("auto_sync", Path(__file__).with_name("auto_sync.py"))
ci = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ci)
REPO = "WN2001/ClashMetaForAndroid"


def result(args, stdout="", code=0):
    return subprocess.CompletedProcess(args, code, stdout, "simulated failure" if code else "")


class BuildRecoveryTests(unittest.TestCase):
    def exercise(self, runs):
        calls = []

        def fake(*args, **kwargs):
            calls.append(args)
            if args[:2] == ("gh", "api"):
                self.assertEqual(args[2], f"repos/{REPO}/commits/main")
                return result(args, "abc123\n")
            self.assertEqual(args[args.index("--repo") + 1], REPO)
            if args[1:3] == ("run", "list"):
                self.assertEqual(args[args.index("--commit") + 1], "abc123")
                self.assertEqual(args[args.index("--branch") + 1], "main")
                return result(args, json.dumps(runs))
            self.assertEqual(args[1:4], ("workflow", "run", ci.WORKFLOW))
            self.assertEqual(args[args.index("--ref") + 1], "main")
            return result(args)

        with patch.object(ci, "command", side_effect=fake), patch.object(ci, "report"):
            ci.ensure_build(REPO)
        return sum(args[1:3] == ("workflow", "run") for args in calls)

    def test_missing_build_dispatches_to_fork(self):
        self.assertEqual(self.exercise([]), 1)

    def test_failed_or_cancelled_build_is_recovered(self):
        for conclusion in ["failure", "cancelled", "timed_out", "skipped", "action_required"]:
            with self.subTest(conclusion=conclusion):
                self.assertEqual(self.exercise([
                    dict(status="completed", conclusion=conclusion, url="run/old"),
                ]), 1)

    def test_active_builds_are_not_duplicated(self):
        for status in ["queued", "in_progress", "waiting", "requested", "pending"]:
            with self.subTest(status=status):
                self.assertEqual(self.exercise([
                    dict(status=status, conclusion="", url="run/active"),
                ]), 0)

    def test_success_is_not_rebuilt_even_if_another_attempt_failed(self):
        self.assertEqual(self.exercise([
            dict(status="completed", conclusion="failure", url="run/new"),
            dict(status="completed", conclusion="success", url="run/good"),
        ]), 0)

    def test_api_failure_is_not_treated_as_missing_build(self):
        with patch.object(ci, "command", return_value=result([], code=1)) as fake:
            with patch.object(ci.time, "sleep"), self.assertRaises(RuntimeError):
                ci.ensure_build(REPO)
        self.assertEqual(fake.call_count, 3)
        self.assertTrue(all(call.args[:2] == ("gh", "api") for call in fake.call_args_list))

    def test_network_read_retries_then_recovers(self):
        with patch.object(ci, "command", side_effect=[result([], code=1), result([], "ok")]):
            with patch.object(ci.time, "sleep"):
                self.assertEqual(ci.retry("gh", "api", "test"), "ok")

    def test_dispatch_failure_is_not_silently_accepted_or_duplicated(self):
        with patch.object(ci, "retry", side_effect=["abc123", "[]"]):
            with patch.object(ci, "command", side_effect=RuntimeError("dispatch failed")) as fake:
                with self.assertRaisesRegex(RuntimeError, "dispatch failed"):
                    ci.ensure_build(REPO)
        self.assertEqual(fake.call_count, 1)


class GitSyncTests(unittest.TestCase):
    def setUp(self):
        self.sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(self.sandbox.cleanup)
        root = Path(self.sandbox.name)
        self.upstream = root / "upstream"
        self.origin = root / "origin.git"
        self.fork = root / "fork"
        self.upstream.mkdir()
        self.git(self.upstream, "init", "-b", "main")
        self.identity(self.upstream)
        self.commit(self.upstream, "theme.txt", "original theme\n", "initial")
        self.git(root, "clone", "--bare", str(self.upstream), str(self.origin))
        self.git(root, "clone", str(self.origin), str(self.fork))
        self.identity(self.fork)
        self.commit(self.fork, "theme.txt", "custom M3 theme\n", "beautify")
        self.git(self.fork, "push", "origin", "main")
        self.original = self.git(self.fork, "rev-parse", "HEAD")
        previous = Path.cwd()
        os.chdir(self.fork)
        self.addCleanup(os.chdir, previous)
        upstream_patch = patch.object(ci, "UPSTREAM", str(self.upstream))
        upstream_patch.start()
        self.addCleanup(upstream_patch.stop)
        self.gh_calls = []
        self.open_pr = []
        self.pr_body = ""
        real_command = ci.command

        def fake_github(*args, **kwargs):
            if args[0] != "gh":
                return real_command(*args, **kwargs)
            self.gh_calls.append(args)
            self.assertEqual(args[args.index("--repo") + 1], REPO)
            if args[1:3] == ("pr", "list"):
                return result(args, json.dumps(self.open_pr))
            self.assertEqual(args[1:3], ("pr", "create"))
            self.assertIn("--draft", args)
            self.pr_body = Path(args[args.index("--body-file") + 1]).read_text(encoding="utf-8")
            return result(args, "https://github.com/WN2001/ClashMetaForAndroid/pull/1")

        command_patch = patch.object(ci, "command", side_effect=fake_github)
        command_patch.start()
        self.addCleanup(command_patch.stop)
        report_patch = patch.object(ci, "report")
        report_patch.start()
        self.addCleanup(report_patch.stop)

    def git(self, cwd, *args):
        return subprocess.run(
            ["git", "-C", str(cwd), *args], check=True, capture_output=True,
            text=True, encoding="utf-8",
        ).stdout.strip()

    def identity(self, directory):
        self.git(directory, "config", "user.name", "CI test")
        self.git(directory, "config", "user.email", "ci-test@example.invalid")
        self.git(directory, "config", "commit.gpgsign", "false")

    def commit(self, directory, name, content, message):
        (directory / name).write_text(content, encoding="utf-8")
        self.git(directory, "add", name)
        self.git(directory, "commit", "-m", message)

    def test_merge_preserves_beautification_and_is_repeatable(self):
        self.commit(self.upstream, "core.txt", "new core\n", "update core")
        ci.sync(REPO)
        merged = self.git(self.fork, "rev-parse", "HEAD")
        self.assertNotEqual(merged, self.original)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), merged)
        self.assertEqual((self.fork / "theme.txt").read_text(), "custom M3 theme\n")
        self.assertEqual((self.fork / "core.txt").read_text(), "new core\n")
        ci.sync(REPO)
        self.assertEqual(self.git(self.fork, "rev-parse", "HEAD"), merged)
        self.assertFalse(self.gh_calls)

    def test_conflict_leaves_main_clean_and_pr_contains_real_upstream(self):
        self.commit(self.upstream, "theme.txt", "upstream theme\n", "upstream UI")
        ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), self.original)
        self.assertEqual(self.git(self.fork, "rev-parse", "HEAD"), self.original)
        self.assertEqual(self.git(self.fork, "status", "--porcelain"), "")
        self.assertEqual(
            self.git(self.origin, "rev-parse", ci.SYNC_BRANCH),
            self.git(self.upstream, "rev-parse", "HEAD"),
        )
        self.assertEqual(self.git(self.origin, "show", f"{ci.SYNC_BRANCH}:theme.txt"), "upstream theme")
        self.assertIn("`theme.txt`", self.pr_body)
        self.assertEqual(len(self.gh_calls), 2)

    def test_existing_conflict_pr_preserves_human_commits(self):
        self.commit(self.upstream, "theme.txt", "upstream theme\n", "upstream UI")
        self.git(self.fork, "push", "origin", f"HEAD:refs/heads/{ci.SYNC_BRANCH}")
        self.open_pr = [dict(number=7, url="https://github.com/test/repo/pull/7")]
        ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", ci.SYNC_BRANCH), self.original)
        self.assertEqual(len(self.gh_calls), 1)

    def test_divergent_conflict_branch_without_pr_is_never_overwritten(self):
        self.commit(self.upstream, "theme.txt", "upstream theme\n", "upstream UI")
        self.git(self.fork, "push", "origin", f"HEAD:refs/heads/{ci.SYNC_BRANCH}")
        with self.assertRaises(RuntimeError):
            ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", ci.SYNC_BRANCH), self.original)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), self.original)
        self.assertEqual(len(self.gh_calls), 1)

    def test_dirty_tree_is_rejected_before_fetch_or_push(self):
        (self.fork / "theme.txt").write_text("unsaved design\n")
        with self.assertRaises(RuntimeError):
            ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), self.original)

    def test_non_main_branch_is_rejected(self):
        self.git(self.fork, "checkout", "-b", "review")
        with self.assertRaises(RuntimeError):
            ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), self.original)

    def test_concurrent_push_cannot_be_overwritten(self):
        other = self.fork.parent / "other"
        self.git(self.fork.parent, "clone", str(self.origin), str(other))
        self.identity(other)
        self.commit(other, "other.txt", "human commit\n", "concurrent change")
        self.git(other, "push", "origin", "main")
        latest = self.git(other, "rev-parse", "HEAD")
        self.commit(self.upstream, "core.txt", "new core\n", "core update")
        with self.assertRaises(RuntimeError):
            ci.sync(REPO)
        self.assertEqual(self.git(self.origin, "rev-parse", "main"), latest)


if __name__ == "__main__":
    unittest.main()
