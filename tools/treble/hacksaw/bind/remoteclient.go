// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package bind

import (
	"errors"
	"net/rpc"
)

//RemoteBinderClient sends client requests to RPC
type RemoteBinderClient struct {
	rpcClient  *rpc.Client
	SocketPath string
}

func NewRemoteBindClient(socketPath string) PathBinder {
	var r RemoteBinderClient
	r.SocketPath = socketPath
	return &r
}

func (r *RemoteBinderClient) initRpcClient() error {
	if r.rpcClient != nil {
		return nil
	}
	var err error
	r.rpcClient, err = rpc.DialHTTP("unix", r.SocketPath)
	return err
}

func (r *RemoteBinderClient) BindReadOnly(source string, destination string) error {
	args := BindReadOnlyArgs{source, destination}
	var reply BindReadOnlyReply
	if err := r.initRpcClient(); err != nil {
		return err
	}
	if err := r.rpcClient.Call("Server.BindReadOnly", &args, &reply); err != nil {
		return err
	}
	if reply.Err != "" {
		return errors.New(reply.Err)
	}
	return nil
}

func (r *RemoteBinderClient) BindReadWrite(source string, destination string) error {
	args := BindReadWriteArgs{source, destination}
	var reply BindReadWriteReply
	if err := r.initRpcClient(); err != nil {
		return err
	}
	if err := r.rpcClient.Call("Server.BindReadWrite", &args, &reply); err != nil {
		return err
	}
	if reply.Err != "" {
		return errors.New(reply.Err)
	}
	return nil
}

func (r *RemoteBinderClient) Unbind(destination string) error {
	args := UnbindArgs{destination}
	var reply UnbindReply
	if err := r.initRpcClient(); err != nil {
		return err
	}
	if err := r.rpcClient.Call("Server.Unbind", &args, &reply); err != nil {
		return err
	}
	if reply.Err != "" {
		return errors.New(reply.Err)
	}
	return nil
}

func (r *RemoteBinderClient) List() ([]string, error) {
	var args ListArgs
	var reply ListReply
	if err := r.initRpcClient(); err != nil {
		return nil, err
	}
	if err := r.rpcClient.Call("Server.List", &args, &reply); err != nil {
		return nil, err
	}

	if reply.Err != "" {
		return nil, errors.New(reply.Err)
	}

	return reply.BindList, nil
}
