#!/usr/bin/env node
// Phoenix AI Agent - CLI 入口
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const args = process.argv.slice(2);
const cmd = args[0] || 'chat';

switch (cmd) {
  case 'chat':
    // 检查本地是否有编译好的 Phoenix
    const homeDir = process.env.HOME || process.env.USERPROFILE;
    const phoenixDir = path.join(homeDir, '.phoenix');
    const phoenixJar = path.join(phoenixDir, 'phoenix.jar');

    if (!fs.existsSync(phoenixJar)) {
      console.log('📦 首次运行，正在安装 Phoenix...');
      // 此处触发 git clone + mvn compile
      console.log('请执行: bash phoenix.sh');
      process.exit(0);
    }

    console.log('🐦 Phoenix AI Agent');
    console.log('启动服务: java -jar phoenix.jar');
    break;

  case 'setup':
    console.log('🚀 Phoenix 配置向导');
    break;

  case 'help':
  default:
    console.log(`
🐦 Phoenix AI Agent CLI

用法:
  npx phoenix         启动对话
  npx phoenix setup   配置向导
  npx phoenix help    帮助信息

安装:
  npm install -g phoenix-agent
`);
    break;
}