import os
import sys
import datetime
import shutil
import re
from pathlib import Path

# ================== 配置区 ==================
PROJECT_ROOT = r"S:\.code_\Object\ai_chat_project\ai_chat_project"
OUTPUT_FILE_BASE = r"S:\.code_\Object\ai_chat_project\ai_chat_project\shell\project_dump"

EXTENSIONS = {'.java', '.xml', '.yml', '.yaml', '.md', '.properties', '.txt' , '.vue' , '.js' , '.css'}
EXCLUDE_DIRS = {'.git', 'target', 'build', 'node_modules', '__pycache__', '.idea', '.vscode'}

# 是否保留历史 dump 文件（支持 True/False/"true"/"false"/"TRUE" 等，忽略大小写）
KEEP_HISTORY = "true"  # ← 可改为 False, "false", "True", True 等
# ===========================================

def parse_bool(value):
    """将多种 true/false 表示法转为 bool，忽略大小写"""
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        val_lower = value.strip().lower()
        if val_lower in ('true', '1', 'yes', 'on'):
            return True
        elif val_lower in ('false', '0', 'no', 'off'):
            return False
    raise ValueError(f"无法解析为布尔值: {value!r}。请使用 true/false、True/False、'true'/'false' 等。")

def read_file_safely(file_path):
    encodings = ['utf-8', 'gbk', 'latin1']
    for enc in encodings:
        try:
            with open(file_path, 'r', encoding=enc) as f:
                return f.read()
        except (UnicodeDecodeError, OSError):
            continue
    return None

def backup_existing_dump(main_file: Path, keep_history: bool):
    if not keep_history:
        return

    if not main_file.exists():
        return

    his_dir = main_file.parent / "his"
    his_dir.mkdir(exist_ok=True)

    timestamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    backup_name = f"{main_file.stem}_{timestamp}{main_file.suffix}"
    backup_path = his_dir / backup_name

    try:
        shutil.move(str(main_file), str(backup_path))
        print(f"📁 已备份现有 dump 文件至: {backup_path}")
    except Exception as e:
        print(f"⚠️  备份失败: {e}")

def is_dump_file(file_path: Path, main_dump_file: Path, project_root: Path) -> bool:
    try:
        if file_path.resolve() == main_dump_file.resolve():
            return True

        rel_to_root = file_path.relative_to(project_root)
        parts = rel_to_root.parts

        if len(parts) >= 3 and parts[-3] == 'shell' and parts[-2] == 'his':
            filename = parts[-1]
            if re.match(rf"^{re.escape(main_dump_file.stem)}_\d{{14}}\.txt$", filename, re.IGNORECASE):
                return True

        return False
    except ValueError:
        return False

def main():
    # 解析 KEEP_HISTORY 配置（支持字符串和布尔）
    try:
        keep_history = parse_bool(KEEP_HISTORY)
    except ValueError as e:
        print(f"❌ 配置错误: {e}")
        return

    project_root = Path(PROJECT_ROOT).resolve()
    if not project_root.exists():
        print(f"❌ 错误：项目路径不存在！\n{project_root}")
        return

    main_output_file = Path(OUTPUT_FILE_BASE).with_suffix(".txt").resolve()
    output_dir = main_output_file.parent
    output_dir.mkdir(parents=True, exist_ok=True)

    backup_existing_dump(main_output_file, keep_history)

    collected_files = []
    for file_path in project_root.rglob('*'):
        if not file_path.is_file():
            continue

        rel_parts = file_path.relative_to(project_root).parts[:-1]
        if any(part in EXCLUDE_DIRS for part in rel_parts):
            continue

        if is_dump_file(file_path, main_output_file, project_root):
            if keep_history or True:
                print(f"⏭️  跳过 dump 文件: {file_path.relative_to(project_root)}")
            continue

        if file_path.suffix.lower() in EXTENSIONS:
            collected_files.append(file_path)

    print(f"🔍 找到 {len(collected_files)} 个目标文件，开始写入新 dump...")

    with open(main_output_file, 'w', encoding='utf-8') as out_f:
        current_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        out_f.write(f"# 全量项目 dump 文件\n")
        out_f.write(f"# 生成时间: {current_time}\n")
        out_f.write(f"# 共 {len(collected_files)} 个文件\n")
        if not keep_history:
            out_f.write("# 注意：历史备份已禁用 (KEEP_HISTORY=false)\n")
        out_f.write("=" * 80 + "\n\n")

        for i, file_path in enumerate(collected_files, 1):
            rel_path = file_path.relative_to(project_root)
            content = read_file_safely(file_path)
            
            if content is None:
                print(f"⚠️  跳过（无法读取）: {rel_path}")
                continue

            out_f.write(f"\n{'='*80}\n")
            out_f.write(f"File: {rel_path}\n")
            out_f.write(f"{'='*80}\n\n")
            out_f.write(content)
            out_f.write("\n")

            if i % 20 == 0:
                print(f"✅ 已处理 {i} / {len(collected_files)} 个文件...")

    print(f"\n🎉 完成！新 dump 文件已生成:\n{main_output_file}")
    print(f"📊 共收集 {len(collected_files)} 个文件。")
    if not keep_history:
        print("ℹ️  历史备份已禁用（旧 dump 文件被直接覆盖）。")

if __name__ == "__main__":
    main()