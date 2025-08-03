#!/usr/bin/env python3

# note - needs
# sudo apt-get install python3-networkx python3-matplotlib
import matplotlib.pyplot as plt
import networkx as nx

import re
import fileinput

# okay - not actually using this now
# groups : 1 -> module name, 2 -> ultimate deps
RE_highlevel = re.compile("error: [^ ]* module \"([^\"]*)\" variant \"[^\"]*\" .*? depends on multiple versions of the same aidl_interface: (.*)\n")

# groups : 1 -> module name
RE_dependencyStart = re.compile("error: [^ ]* module \"([^\"]*)\" variant \"[^\"]*\" .*? Dependency path.*\n")

# groups : 1 -> module name
RE_dependencyCont = re.compile(" *-> ([^{]*){.*\n")

RE_ignore= re.compile(" *via tag.*{.*}.*\n")

# [(module, module)]
graph = []

last = None

for i in fileinput.input():
    # could validate consistency of this graph based on this
    if RE_highlevel.fullmatch(i): continue

    m = RE_dependencyStart.fullmatch(i)
    if m:
        last = m.groups()[0]
        continue

    m = RE_dependencyCont.fullmatch(i)
    if m:
        curr = m.groups()[0]
        graph += [(last, curr)]
        last = curr
        continue

    if RE_ignore.fullmatch(i): continue

    print("UNRECOGNIZED LINE", i.strip())

#for a,b in graph:
#    print(a,b)

G = nx.MultiDiGraph()
G.add_edges_from(graph)
plt.figure(figsize=(10,10))
nx.draw(G, connectionstyle='arc3,rad=0.01', with_labels=True)
plt.show()

