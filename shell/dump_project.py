import os
import sys
import datetime
import shutil
import re
from pathlib import Path

# ================== 配置区 ==================
# 源项目根目录（请确保路径正确）
PROJECT_ROOT = r"S:\.code_\Object\ai_chat_project\ai_chat_project"

# 输出文件路径（将自动追加时间戳）
OUTPUT_FILE_BASE = r"S:\.code_\Object\ai_chat_project\ai_chat_project\shell\project_dump"

# 要收集的文件扩展名（小写）
EXTENSIONS = {'.java', '.xml', '.yml', '.yaml', '.md', '.properties', '.txt'}

# 可选：排除某些目录（如 node_modules, .git 等）
EXCLUDE_DIRS = {'.git', 'target', 'build', 'node_modules', '__pycache__', '.idea', '.vscode'}
# ===========================================

def read_file_safely(file_path):
    """尝试用多种编码读取文件，返回内容或 None"""
    encodings = ['utf-8', 'gbk', 'latin1']
    for enc in encodings:
        try:
            with open(file_path, 'r', encoding=enc) as f:
                return f.read()
        except (UnicodeDecodeError, OSError):
            continue
    return None  # 无法读取（可能是二进制文件）

def archive_old_dumps(output_dir: Path, base_name: str):
    """
    将 output_dir 下匹配 base_name_YYYYMMDDHHMMSS.txt 的旧文件
    移动到 output_dir/his/ 目录下
    """
    # 构造正则表达式：base_name + _ + 14位数字 + .txt
    pattern = re.compile(rf"^{re.escape(base_name)}_\d{{14}}\.txt$")
    
    his_dir = output_dir / "his"
    his_dir.mkdir(exist_ok=True)

    moved_count = 0
    for item in output_dir.iterdir():
        if item.is_file() and pattern.match(item.name):
            try:
                shutil.move(str(item), str(his_dir / item.name))
                print(f"📁 归档历史文件: {item.name}")
                moved_count += 1
            except Exception as e:
                print(f"⚠️  无法归档 {item.name}: {e}")
    
    if moved_count > 0:
        print(f"✅ 已归档 {moved_count} 个历史 dump 文件到 {his_dir}")

def main():
    project_root = Path(PROJECT_ROOT)
    if not project_root.exists():
        print(f"❌ 错误：项目路径不存在！\n{project_root}")
        return

    # 解析输出目录和基础文件名
    output_base = Path(OUTPUT_FILE_BASE)
    output_dir = output_base.parent
    base_filename = output_base.name  # 例如 "project_dump"

    # 确保输出目录存在
    output_dir.mkdir(parents=True, exist_ok=True)

    # 归档旧的历史 dump 文件
    archive_old_dumps(output_dir, base_filename)

    # 生成带时间戳的新输出文件名
    timestamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    output_filename = f"{base_filename}_{timestamp}.txt"
    output_path = output_dir / output_filename

    collected_files = []
    
    # 遍历所有文件
    for file_path in project_root.rglob('*'):
        if file_path.is_file():
            # 跳过排除的目录
            rel_parts = file_path.relative_to(project_root).parts[:-1]
            if any(part in EXCLUDE_DIRS for part in rel_parts):
                continue
            
            # 检查扩展名
            if file_path.suffix.lower() in EXTENSIONS:
                collected_files.append(file_path)

    print(f"🔍 找到 {len(collected_files)} 个目标文件，开始读取...")

    with open(output_path, 'w', encoding='utf-8') as out_f:
        for i, file_path in enumerate(collected_files, 1):
            rel_path = file_path.relative_to(project_root)
            content = read_file_safely(file_path)
            
            if content is None:
                print(f"⚠️  跳过（无法读取）: {rel_path}")
                continue

            # 写入分隔符 + 路径 + 内容
            out_f.write(f"\n{'='*80}\n")
            out_f.write(f"File: {rel_path}\n")
            out_f.write(f"{'='*80}\n\n")
            out_f.write(content)
            out_f.write("\n")  # 确保文件末尾有换行

            if i % 20 == 0:
                print(f"✅ 已处理 {i} / {len(collected_files)} 个文件...")

    print(f"\n🎉 完成！输出文件已保存至:\n{output_path.absolute()}")
    print(f"📊 总共收集了 {len(collected_files)} 个文件。")

if __name__ == "__main__":
    main()