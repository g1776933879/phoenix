#!/bin/bash
# Phoenix Homebrew 打包脚本
# 用法: brew install --cask phoenix

set -euo pipefail

APP="phoenix"
VERSION="${1:-1.0.0}"
HOMEBREW_DIR="${HOMEBREW_PREFIX:-/usr/local}/Homebrew/Library/Taps/homebrew/homebrew-cask"

echo "📦 Packaging Phoenix $VERSION for Homebrew..."

# 生成 Homebrew Cask 配方
cat > "phoenix.rb" << EOF
cask "phoenix" do
  version "${VERSION}"
  sha256 :no_check

  url "https://github.com/g1776933879/phoenix.git",
      tag:      "v#{version}",
      revision: "HEAD"

  name "Phoenix AI Agent"
  desc "企业级AI Agent框架 - ReAct循环 · 四层记忆 · 多渠道"
  homepage "https://github.com/g1776933879/phoenix"

  depends_on "openjdk@21"
  depends_on "maven"

  app "phoenix"

  postflight do
    system_command "mvn",
                   args: ["clean", "install", "-Dmaven.test.skip=true",
                          "-pl", "your-agent-core,your-agent-spring-boot-starter,your-business-app",
                          "-am", "-q"],
                   chdir: "#{staged_path}"
  end

  binary "phoenix.sh", target: "phoenix"

  zap trash: [
    "~/.phoenix",
    "~/.agent/sessions.db",
    "~/.agent/long_term.db"
  ]
end
EOF

echo "✅ Homebrew formula created: phoenix.rb"
echo ""
echo "安装方式:"
echo "  brew install --cask phoenix.rb"
echo "  brew install phoenix"
echo ""
echo "发布到 Homebrew:"
echo "  1. Fork https://github.com/Homebrew/homebrew-cask"
echo "  2. 将 phoenix.rb 添加到 Casks/ 目录"
echo "  3. 提交 PR"