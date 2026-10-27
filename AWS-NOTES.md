# AWS Notes

Log of every AWS resource created for this project (region: `us-east-2`) and how to delete it.

Account `769093515641` is in an AWS-managed organization whose SCP allows only `us-east-2`; calls to other regions fail with "explicit deny in a service control policy". Free plan: $120 credits, expires 2027-04-26.

| Date | Resource | Name / ID | Purpose | Delete command |
|------|----------|-----------|---------|----------------|
| 2026-10-26 | AWS Budget (global) | `My Zero-Spend Budget` | Email alert if any spend > $0.01 (free) | `aws budgets delete-budget --account-id 769093515641 --budget-name "My Zero-Spend Budget"` |
| 2026-10-26 | IAM user (global) + access key | `sujan-admin` (AdministratorAccess) | CLI identity on laptop; key in `~/.aws/credentials` (free) | In console: IAM → Users → sujan-admin → delete access key, then delete user. (Can't delete yourself via CLI while using its key.) |
| 2026-10-26 | EC2 key pair (us-east-2) | `rideai-key`; private key at `~/.ssh/rideai-key.pem` | SSH into the server (free) | `aws ec2 delete-key-pair --key-name rideai-key --region us-east-2 && rm -f ~/.ssh/rideai-key.pem` |
| 2026-10-26 | Security group (us-east-2) | `rideai-sg` / `sg-09c211aa1dbfddb8d` in default VPC | Firewall: 22 from 129.219.8.0/32; 80 + 443 (Caddy/HTTPS) from anywhere (free). 8080 was opened, then closed 2026-10-27 | `aws ec2 delete-security-group --group-id sg-09c211aa1dbfddb8d --region us-east-2` (after the instance using it is terminated) |
| 2026-10-26 | EC2 instance (us-east-2a) | `rideai` / `i-05616d023c41f054c`, t3.small, AL2023, CpuCredits=standard | App server (Docker Compose). ~$0.021/h + ~$0.005/h public IP while running. **Stop when idle.** | `aws ec2 terminate-instances --instance-ids i-05616d023c41f054c --region us-east-2` |
| 2026-10-26 | EBS volume (us-east-2a) | `vol-06c132404c58131b6`, 20 GB gp3 | Root disk of `rideai`. ~$1.60/month even when stopped | Deleted automatically when the instance is terminated (DeleteOnTermination) |

## Outside AWS

| Date | What | Notes | Delete |
|------|------|-------|--------|
| 2026-10-27 | DuckDNS subdomain `rideai-sujanuj.duckdns.org` (free, login: GitHub sujanuj) | The `duckdns` container updates it to the server's IP every 5 min. Token lives only in the server's `~/rideai/.env` | duckdns.org → delete domain |
| 2026-10-27 | Let's Encrypt certificate (free) | Caddy gets and renews it; stored in the `rideai_caddy_data` Docker volume | Nothing to delete; expires by itself |

Site: **https://rideai-sujanuj.duckdns.org**, which keeps working after stop/start (wait ~5 min for DuckDNS to catch up).

## Everyday commands

```bash
# Stop (saves credits; disk stays)
aws ec2 stop-instances --instance-ids i-05616d023c41f054c --region us-east-2
# Start (gets a NEW public IP each time)
aws ec2 start-instances --instance-ids i-05616d023c41f054c --region us-east-2
# Current state + IP
aws ec2 describe-instances --instance-ids i-05616d023c41f054c --region us-east-2 \
  --query 'Reservations[].Instances[].[State.Name,PublicIpAddress]' --output text
# SSH in
ssh -i ~/.ssh/rideai-key.pem ec2-user@<public-ip>
```
