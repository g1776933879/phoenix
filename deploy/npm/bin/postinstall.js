#!/usr/bin/env node
// Phoenix npm postinstall 脚本
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

console.log('🐦 Phoenix AI Agent 安装中...');

const homeDir = process.env.HOME || process.env.USERPROFILE;
const phoenixDir = path.join(homeDir, '.phoenix');

if (!fs.existsSync(phoenixDir)) {
  fs.mkdirSync(phoenixDir, { recursive: true });
}

console.log('✅ Phoenix 安装完成！');
console.log('运行: npx phoenix chat');