#!/usr/bin/env python3
import os
import glob

config_dir = "configs/experiment1new"
config_files = glob.glob(f"{config_dir}/*.cosc")

for config_file in sorted(config_files):
    print(f"Processing {os.path.basename(config_file)}...")

    with open(config_file, 'r') as f:
        lines = f.readlines()

    new_lines = []
    section = None
    counter = 0

    for line in lines:
        stripped = line.strip()

        # Check for section headers
        if stripped.startswith("GPU:"):
            section = "GPU"
            counter = int(stripped.split(":")[1])
            new_lines.append(line)
        elif stripped.startswith("CPU:"):
            section = "CPU"
            counter = int(stripped.split(":")[1])
            new_lines.append(line)
        elif stripped.startswith("MIXED:"):
            section = "MIXED"
            counter = int(stripped.split(":")[1])
            new_lines.append(line)
        elif stripped.startswith("["):
            section = None
            counter = 0
            new_lines.append(line)
        else:
            # Process VM lines
            if (section == "GPU" or section == "MIXED") and counter > 0 and "," in line:
                parts = line.strip().split(",")
                if len(parts) == 7:
                    # Change GPU count (4th field, index 3) from 0 to 1
                    parts[3] = "1"
                    new_lines.append(",".join(parts) + "\n")
                    counter -= 1
                else:
                    new_lines.append(line)
            else:
                new_lines.append(line)
                if counter > 0 and stripped and not stripped.startswith("#"):
                    counter -= 1

    # Write back
    with open(config_file, 'w') as f:
        f.writelines(new_lines)

    print(f"  ✓ Updated {os.path.basename(config_file)}")

print(f"\n✓ Successfully updated all {len(config_files)} configuration files")
