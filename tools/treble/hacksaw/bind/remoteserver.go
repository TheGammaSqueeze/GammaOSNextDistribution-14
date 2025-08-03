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

//Server receives RPC requests
type Server struct {
	priv PathBinder
}

func NewServer(binder PathBinder) *Server {
	var s Server
	s.priv = binder
	return &s
}

type BindReadOnlyArgs struct {
	Source      string
	Destination string
}

type BindReadOnlyReply struct {
	// Errors types cannot be passed as-is in RPC so they
	// must be converted to plain strings.
	// Details at https://github.com/golang/go/issues/23340
	Err string
}

func (s Server) BindReadOnly(args *BindReadOnlyArgs, reply *BindReadOnlyReply) error {
	if err := s.priv.BindReadOnly(args.Source, args.Destination); err != nil {
		reply.Err = err.Error()
	}
	return nil
}

type BindReadWriteArgs struct {
	Source      string
	Destination string
}

type BindReadWriteReply struct {
	// Errors types cannot be passed as-is in RPC so they
	// must be converted to plain strings.
	// Details at https://github.com/golang/go/issues/23340
	Err string
}

func (s Server) BindReadWrite(args *BindReadWriteArgs, reply *BindReadWriteReply) error {
	if err := s.priv.BindReadWrite(args.Source, args.Destination); err != nil {
		reply.Err = err.Error()
	}
	return nil
}

type UnbindArgs struct {
	Destination string
}

type UnbindReply struct {
	// Errors types cannot be passed as-is in RPC so they
	// must be converted to plain strings.
	// Details at https://github.com/golang/go/issues/23340
	Err string
}

func (s Server) Unbind(args *UnbindArgs, reply *UnbindReply) error {
	if err := s.priv.Unbind(args.Destination); err != nil {
		reply.Err = err.Error()
	}
	return nil
}

type ListArgs struct {
}

type ListReply struct {
	BindList []string
	// Errors types cannot be passed as-is in RPC so they
	// must be converted to plain strings.
	// Details at https://github.com/golang/go/issues/23340
	Err string
}

func (s Server) List(args *ListArgs, reply *ListReply) error {
	bindList, err := s.priv.List()
	if err != nil {
		reply.Err = err.Error()
	}
	reply.BindList = bindList
	return nil
}
