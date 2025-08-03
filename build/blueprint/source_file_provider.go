package blueprint

type SrcsFileProviderData struct {
	SrcPaths []string
}

var SrcsFileProviderKey = NewProvider[SrcsFileProviderData]()
