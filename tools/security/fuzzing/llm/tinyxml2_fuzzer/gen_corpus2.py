import xml.etree.ElementTree as ET
import random
import os

def generate_random_xml(depth=0, max_depth=3, num_attributes=2, max_text_length=20):
    """
    Recursively generates a random XML structure.

    Args:
        depth: Current nesting depth.
        max_depth: Maximum allowed nesting depth.
        num_attributes: Number of attributes to generate for each element.
        max_text_length: Maximum length for text content in elements.

    Returns:
        ET.Element: The generated XML element.
    """

    tag_name = f"element_{random.randint(1, 100)}"
    element = ET.Element(tag_name)

    # Add attributes
    for _ in range(random.randint(0, num_attributes)):
        attr_name = f"attr_{random.randint(1, 10)}"
        attr_value = "".join(random.choices("abcdefghijklmnopqrstuvwxyz ", k=random.randint(0, 15)))
        element.set(attr_name, attr_value)

    # Add text content
    text_content = "".join(random.choices("abcdefghijklmnopqrstuvwxyz  <>&\"'", k=random.randint(0, max_text_length)))
    element.text = text_content

    # Recursively add child elements
    if depth < max_depth:
        num_children = random.randint(0, 3)
        for _ in range(num_children):
            child = generate_random_xml(depth + 1, max_depth, num_attributes, max_text_length)
            element.append(child)

    return element


if __name__ == "__main__":
    num_files = 100  # Number of XML files to generate
    corpus_dir = "xml_corpus"  # Directory to store the generated files

    os.makedirs(corpus_dir, exist_ok=True)

    for i in range(num_files):
        root_element = generate_random_xml()
        tree = ET.ElementTree(root_element)

        # Add corruptions (optional)
        if random.random() < 0.2:  # 20% chance of introducing corruption
            corruption_type = random.choice(["missing_end_tag", "invalid_attribute", "unescaped_chars"])
            # ... add logic to introduce the specific corruption type ...

        # Save to file
        filename = os.path.join(corpus_dir, f"sample_{i}.xml")
        tree.write(filename, encoding="utf-8", xml_declaration=True)
