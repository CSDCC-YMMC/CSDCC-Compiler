import re
import os
import subprocess
import glob
import threading
import argparse
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed

TEST_IR = True
TEST_ASM = False
TIMEOUT = 1200
ROOT_DIR = os.path.abspath(os.path.join(os.path.abspath(__file__), '../..'))
TEST_DIR = f'{ROOT_DIR}/tests'
CP = '../compiler.jar'
TIME_PATTERN = re.compile(r'TOTAL: (\d+)H-(\d+)M-(\d+)S-(\d+)us')
TEST_CASES = []
pass_cnt = 0
pass_cnt_lock = threading.Lock()

def init():
    subprocess.run('make build', cwd=ROOT_DIR, shell=True)
    lib_file = 'lib/libsysy.so'
    cmd_list = [
        f'clang -shared -fPIC lib/sylib.c -o {lib_file}',
        f'arm-linux-gnueabihf-gcc -c lib/sylib.c -o lib/libsysy.o',
        f'arm-linux-gnueabihf-ar rcs lib/libsysy.a lib/libsysy.o',
    ]
    for cmd in cmd_list:
        print(f'Running {cmd}')
        subprocess.run(cmd, cwd=TEST_DIR, shell=True)
    os.environ['LD_LIBRARY_PATH'] = f'{TEST_DIR}/lib'

    # setup testcases
    dir_list = ['testcases']
    for dir in dir_list:
        testcase_dir = f'{TEST_DIR}/{dir}'
        test_files = glob.glob(os.path.join(testcase_dir, '*.sy'))
        input_files = glob.glob(os.path.join(testcase_dir, '*.in'))
        ans_files = glob.glob(os.path.join(testcase_dir, '*.out'))
        f = lambda x: [s[len(TEST_DIR) + 1:] if s.startswith(TEST_DIR) else s for s in x]
        test_files = f(test_files)
        input_files = f(input_files)
        ans_files = f(ans_files)

        for test_file in test_files:
            input_file = f'{test_file.replace(".sy", ".in")}'
            ans_file = f'{test_file.replace(".sy", ".out")}'
            case = {'test_file': test_file}
            if input_file in input_files:
                case['input_file'] = input_file
            if ans_file in ans_files:
                case['ans_file'] = ans_file
            TEST_CASES.append(case)

def convert_time_to_us(match):
    hours, minutes, seconds, microseconds = map(int, match.groups())
    total_us = (hours * 3600 + minutes * 60 + seconds) * 1_000_000 + microseconds
    return total_us

def create_folder_for(file):
    parent_dir = f'{TEST_DIR}/{os.path.dirname(file)}'
    if not os.path.exists(parent_dir):
        try:
            os.makedirs(parent_dir)
        except:
            return

def ensure_newline_at_end_of_file(file_path):
    with open(f'{TEST_DIR}/{file_path}', 'a+') as file:
        file.seek(0, os.SEEK_END)
        if file.tell() > 0:
            file.seek(file.tell() - 1, os.SEEK_SET)
            last_char = file.read(1)
            if last_char != '\n':
                file.write('\n')
        file.seek(0, os.SEEK_END)

def append_return(file_path, return_val):
    ensure_newline_at_end_of_file(file_path)
    with open(f'{TEST_DIR}/{file_path}', 'a+') as file:
        file.write(f'{return_val}\n')

def check(stop_event, test_file, input_file='', ans_file=''):
    time_res = {}
    filename = os.path.splitext(test_file)[0]
    ir_file = f'ir/{filename}.ll'
    ir_runnable = f'ir/{filename}'
    ir_std = f'ir_std/{filename}.ll'
    ir_std_runnable = f'ir_std/{filename}'
    asm_file = f'asm/{filename}.s'
    asm_runnable = f'asm/{filename}'
    output_file = f'output/{test_file}'

    """
    run compiler
    """
    try:
        create_folder_for(ir_file)
        create_folder_for(asm_file)
        cmd = f'java -jar {CP} -f {test_file} -ll {ir_file} -o {asm_file}'
        print(f'Running: {cmd}')
        subprocess.check_output(cmd, cwd=TEST_DIR, shell=True, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        print(f'[ERROR FILE] {test_file}')
        print(f'Error running compiler for {test_file}:\n{e.output.decode()}')
        stop_event.set()
        return False

    time_output = ''

    """
    link and run ir
    """
    if TEST_IR:
        try:
            cmd = f'clang {ir_file} -L./lib -lsysy -o {ir_runnable}'
            print(f'Running: {cmd}')
            subprocess.check_output(cmd, cwd=TEST_DIR, shell=True, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as e:
            print(f'[ERROR FILE] {test_file}')
            print(f'Error running clang for {ir_file}:\n{e.output.decode()}')
            stop_event.set()
            return False

        try:
            create_folder_for(output_file)
            cmd = f'./{ir_runnable} < {input_file} > {output_file}'
            if not input_file:
                cmd = f'./{ir_runnable} > {output_file}'
            print(f'Running: {cmd}')
            r = subprocess.check_output(cmd, cwd=TEST_DIR, shell=True, stderr=subprocess.STDOUT)
            time_output = r.decode()
            append_return(output_file, 0)
        except subprocess.CalledProcessError as e:
            if TIME_PATTERN.search(e.output.decode()):
                append_return(output_file, e.returncode)
                time_output = e.output.decode()
            else:
                print(f'[ERROR FILE] {test_file}')
                print(f'Error running {ir_runnable}: {e.output.decode()}')
                stop_event.set()
                return False

    """
    run asm
    """
    if TEST_ASM:
        create_folder_for(asm_runnable)
        create_folder_for(output_file)
        cmd = f'arm-linux-gnueabihf-gcc -march=armv7-a -static -g {asm_file} lib/libsysy.a -o {asm_runnable}'
        print(f'Running {cmd}')
        res = subprocess.run(cmd, cwd=TEST_DIR, shell=True, capture_output=True, text=True)
        cmd = f'qemu-arm-static {asm_runnable} < {input_file} > {output_file}'
        if not input_file:
            cmd = f'qemu-arm-static {asm_runnable} > {output_file}'
        print(f'Running {cmd}')
        res = subprocess.run(cmd, cwd=TEST_DIR, shell=True, capture_output=True, text=True)
        append_return(output_file, res.returncode)
        time_output = res.stderr

    """
    get TOTAL time
    """
    m = TIME_PATTERN.search(time_output)
    if m or time_output == '':
        time_res['my'] = convert_time_to_us(m) if m else 0
    else:
        print(f'[ERROR FILE] {test_file}')
        print(f'Error running {asm_runnable}: {res.stderr}. Full res: {res}')
        stop_event.set()
        return False
    
    """
    get std answer
    """
    if not ans_file:
        ans_file = f'ans/{test_file}'
        create_folder_for(ir_std)
        subprocess.run(f'clang -x c -S -emit-llvm {test_file} -o {ir_std} -O0 2>/dev/null', cwd=TEST_DIR, shell=True)
        subprocess.run(f'clang {ir_std} -L./lib -lsysy -o {ir_std_runnable}', cwd=TEST_DIR, shell=True)
        create_folder_for(ans_file)
        cmd = f'./{ir_std_runnable} < {input_file} > {ans_file}'
        if not input_file:
            cmd = f'./{ir_std_runnable} > {ans_file}'
        subprocess.run(cmd, cwd=TEST_DIR, shell=True)

    """
    get clang time
    """
    for opt in range(1, 4):
        create_folder_for(ir_std_runnable)
        subprocess.run(f'clang -x c {test_file} lib/sylib.c -include lib/sylib.h -Wall -Wno-unused-result -O{opt} -o {ir_std_runnable}', cwd=TEST_DIR, shell=True, stderr=subprocess.PIPE)
        cmd = f'./{ir_std_runnable} < {input_file}'
        if not input_file:
            cmd = f'./{ir_std_runnable}'
        print(f'Running {cmd}')
        res = subprocess.run(cmd, cwd=TEST_DIR, shell=True, capture_output=True, text=True)
        e = res.stderr
        m = TIME_PATTERN.search(e)
        if m or e == '':
            time_res[f'O{opt}'] = convert_time_to_us(m) if m else 0
        else:
            print(f'ERROR: {e}')

    ensure_newline_at_end_of_file(output_file)
    ensure_newline_at_end_of_file(ans_file)
    cmd = f"bash -c 'diff -w <(tr -d \"\\r\" < {output_file}) {ans_file}'"
    r = subprocess.run(cmd, cwd=TEST_DIR, shell=True, capture_output=True, text=True, check=False)
    if r.stdout:
        print(f'[ERROR FILE] {test_file}')
        print(f'{test_file} WA')
        print(r.stdout)
        stop_event.set()
        return False
    print(f'{test_file} AC')
    with pass_cnt_lock:
        global pass_cnt
        pass_cnt += 1
    return True, time_res

def check_wrapper(stop_event, case, results, results_lock):
    def target():
        try:
            res = check(stop_event, **case)
            if isinstance(res, tuple) and res[0]:
                with results_lock:
                    results.append((case['test_file'], res[1]))
        except Exception as e:
            print(f'{case["test_file"]}: {e}')

    thread = threading.Thread(target=target)
    thread.start()
    thread.join(TIMEOUT)

    if thread.is_alive():
        print(f'{case["test_file"]}: TLE')
        raise TimeoutError(f'{case} TLE')
    return True

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Process some input arguments.")
    parser.add_argument("input", choices=["ir", "asm"], help="Input type: 'ir' or 'asm'")
    args = parser.parse_args()
    if args.input == "ir":
        TEST_IR = True
        TEST_ASM = False
    elif args.input == "asm":
        TEST_IR = False
        TEST_ASM = True

    stop_event = threading.Event()
    init()

    results = []
    results_lock = threading.Lock()
    with ThreadPoolExecutor(max_workers=16) as executor:
        tasks = {executor.submit(check_wrapper, stop_event, case, results, results_lock): case for case in TEST_CASES}
        
        for task in as_completed(tasks):
            if stop_event.is_set():
                for t in tasks:
                    if not t.done():
                        t.cancel()
                exit(1)
            try:
                res = task.result()
                if not res:
                    case = tasks[task]
                    print(f'Stopping due to error in processing {case}')
                    stop_event.set()
                    exit(1)
            except TimeoutError as e:
                stop_event.set()
                for future, task in tasks.items():
                    if not future.done():
                        print(f'{task["test_file"]}: TLE')
                        future.cancel()
                exit(1)
            except Exception as e:
                print(f'Exception: {e}')
                stop_event.set()
                exit(1)

    print(f'test_num: {len(TEST_CASES)}, pass_num: {pass_cnt}')
    df = pd.DataFrame([{'file': file, **res} for file, res in results])
#     df = df[df['my'] != 0]
    df = df.sort_values(by='my', ascending=False)

    df['my/O1'] = df.apply(lambda row: row['my'] / row['O1'] if row['O1'] != 0 else float('inf'), axis=1)
    df['my/O2'] = df.apply(lambda row: row['my'] / row['O2'] if row['O2'] != 0 else float('inf'), axis=1)

    print(df.to_string(index=False))

