Generating test image:
```
echo test > file.txt
echo "file.txt=file.txt" > manifest
zbi -o test_image.img manifest
rm file.txt manifest
```
