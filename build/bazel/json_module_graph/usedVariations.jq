# CMD: Prints the set of variations and their values used in the input

[[.[] | .Variations | select(. != null) | to_entries] |
  flatten |
  group_by(.key) |
  .[] |
  {key: .[0].key, value: map(.value) | unique}] |
  from_entries
