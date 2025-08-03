import os
import random
import string

def generate_random_string(length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def generate_random_xml(depth=0):
    if depth > 3:  # Limit the depth to prevent overly complex XML
        return ""

    tag = generate_random_string(random.randint(1, 10))
    attributes = " ".join(
        f'{generate_random_string(random.randint(1, 5))}="{generate_random_string(random.randint(1, 10))}"'
        for _ in range(random.randint(0, 3))
    )
    content = generate_random_string(random.randint(0, 20))

    children = "".join(generate_random_xml(depth + 1) for _ in range(random.randint(0, 2)))

    return f"<{tag} {attributes}>{content}{children}</{tag}>"

def create_corpus_file(filename, content):
    with open(filename, "w") as file:
        file.write(content)

def main():
    corpus_dir = "xml_corpus"
    os.makedirs(corpus_dir, exist_ok=True)

    # Generate a range of XML files
    for i in range(100):
        xml_content = generate_random_xml()
        filename = os.path.join(corpus_dir, f"xml_{i}.xml")
        create_corpus_file(filename, xml_content)

    # Generate some specific edge cases
    create_corpus_file(os.path.join(corpus_dir, "empty.xml"), "")
    create_corpus_file(os.path.join(corpus_dir, "large.xml"), generate_random_xml() * 1000)

if __name__ == "__main__":
    main()
