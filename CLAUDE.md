# CLAUDE.md

## AWS deployment rules

### Teaching
- I'm learning AWS and DevOps. The first time an AWS concept comes up (VPC, IAM role, security group, ECS, etc.), explain it briefly in a sentence or two.

### Budget: $0
- I'm on the AWS Free plan. Only use services and sizes covered by free-tier credits.
- Never create NAT Gateways, EKS, MSK, or anything else outside the free tier.
- Before any command that creates AWS resources, say what it creates and roughly what it costs, then wait for my OK.

### Region
- Always use `us-east-2` (Ohio). Pass `--region us-east-2` explicitly or confirm `aws configure get region` returns it.
- The account's organization SCP denies every other region (us-west-2, us-east-1, ...), even for admins. Global services (IAM, Budgets) still work.

### Secrets
- Never commit secrets: `.env` files, AWS access keys, `ANTHROPIC_API_KEY`, `JWT_SECRET`.
- Check `.gitignore` before committing, and check `git diff --cached` for secrets.

### Working style
- Work in small steps I can verify.
- After each step, give me the command to check it worked (e.g. an `aws ... describe-*` call or a `curl`).

### Resource log
- Keep `AWS-NOTES.md` at the repo root as a log of every AWS resource we create: name/ID, region, purpose, date, and the exact command to delete it.
- Update it in the same step that creates the resource.
