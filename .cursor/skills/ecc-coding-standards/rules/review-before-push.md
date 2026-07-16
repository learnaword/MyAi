# Review Changes Before Pushing

Always review your changes before pushing to remote.

## Before `git push`
1. Run `git diff` to review unstaged changes
2. Run `git diff --staged` to review staged changes
3. Run `git log --oneline -5` to verify commit history
4. Ensure no secrets, debug code, or incomplete work is included

## Checklist
- [ ] All changes are intentional
- [ ] No hardcoded secrets or API keys
- [ ] No console.log or debug statements
- [ ] Commit messages follow conventional format
- [ ] Tests pass locally
