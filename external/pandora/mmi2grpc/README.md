# mmi2grpc

## Install

```bash
git submodule update --init

pip install [-e] bt-test-interfaces/python
pip install [-e] .
```

## Rebuild gRPC Bluetooth test interfaces

```bash
pip install grpcio-tools==1.46.3
./bt-test-interfaces/python/_build/grpc.py
```
