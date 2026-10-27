#!/bin/bash
# Runs once as root on the EC2 instance's first boot (Amazon Linux 2023).
# Installs Docker + Compose and adds swap so the stack fits in 2 GB of RAM.
# Progress: sudo cat /var/log/cloud-init-output.log
set -euxo pipefail

# 2 GB swap file: spare "RAM" on disk for memory spikes (Kafka + JVM)
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap defaults 0 0' >> /etc/fstab

# Docker, started now and on every boot
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose v2 plugin (not packaged for Amazon Linux)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
