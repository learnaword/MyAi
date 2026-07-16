# No console.log in Production Code

Remove all `console.log` statements before committing.

## Why
- Clutters production output
- May leak sensitive data
- Indicates incomplete debugging

## Instead
- Use a proper logger (e.g., `winston`, `pino`) for server-side logging
- Use conditional debug logging: `if (process.env.NODE_ENV === 'development')`
- Remove debug logs entirely before committing

## Checklist
- [ ] No `console.log` in committed code
- [ ] Use structured logging for production
- [ ] Debug logging gated behind environment check
