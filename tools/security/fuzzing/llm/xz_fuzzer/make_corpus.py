import lzma
import os
import random

def create_valid_xz_files(directory, num_files=10):
    for i in range(num_files):
        file_content = os.urandom(random.randint(100, 10000))  # Random binary content
        file_path = os.path.join(directory, f'valid_file_{i}.xz')

        with lzma.open(file_path, 'wb', preset=random.choice([0, 9])) as f:
            f.write(file_content)

def create_malformed_xz_files(directory, num_files=10):
    for i in range(num_files):
        valid_file_path = random.choice(os.listdir(directory))
        with open(os.path.join(directory, valid_file_path), 'rb') as f:
            content = f.read()

        malformed_content = corrupt_data(content)

        with open(os.path.join(directory, f'malformed_file_{i}.xz'), 'wb') as f:
            f.write(malformed_content)

def corrupt_data(data):
    # Introduce random corruption in data
    index = random.randint(0, len(data) - 1)
    corrupted_data = data[:index] + random.randint(0, 255).to_bytes(1, 'little') + data[index + 1:]
    return corrupted_data

def main():
    corpus_directory = 'xz_corpus'
    os.makedirs(corpus_directory, exist_ok=True)

    # Create valid .xz files
    create_valid_xz_files(corpus_directory, num_files=50)

    # Create malformed .xz files
    create_malformed_xz_files(corpus_directory, num_files=50)

if __name__ == '__main__':
    main()
