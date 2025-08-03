# CMD: Returns the names of properties used by $arg

[.[] |
  select (.Name == $arg) |
  .Module.Android.SetProperties |
  map(.Name)] |
  flatten | unique | sort
