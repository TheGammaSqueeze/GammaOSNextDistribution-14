#!/usr/bin/env bash

impl_path="doclet_adapter/src/main/java/com/google/doclava/javadoc"

used_total=$(printf '%d' $(grep -ri '@Used' $impl_path | wc -l))
used_implemented=$(printf '%d' $(grep -ri '@Used(implemented = true)' | wc -l))
unused_total=$(printf '%d' $(grep -ri '@Unused' $impl_path | wc -l))
unused_implemented=$(printf '%d' $(grep -ri '@Unused(implemented = true)' $impl_path | wc -l))

total=$(($used_total + $unused_total))
total_implemented=$(($used_implemented + $unused_implemented))
perc_impl_of_used=$(bc <<< "scale=2; $used_implemented * 100 / $used_total")
perc_impl_of_unused=$(bc <<< "scale=2; $unused_implemented * 100 / $unused_total")
perc_impl_of_all=$(bc <<< "scale=2; ($used_implemented + $unused_implemented) * 100 / $total")

echo "Current progress: ${perc_impl_of_used}%

== Methods in implementation classes ==
Total (used+unused)
    total:       ${total} methods
    implemented: ${total_implemented}/${total} (${perc_impl_of_all}%)
Used
    total:       ${used_total}
    implemented: ${used_implemented}/${used_total} (${perc_impl_of_used}%)
Unused
    total:       ${unused_total}
    implemented: ${unused_implemented}/${unused_total} (${perc_impl_of_unused}%)
"
