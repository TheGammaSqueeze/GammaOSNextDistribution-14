"""
    Flask server for hosting Lua Interpreter tool.
"""

import ctypes
import json
import os
import flask

app = flask.Flask(__name__)
lua_lib = ctypes.cdll.LoadLibrary('./liblua_engine.so')
lua_lib.NewLuaEngine.argtypes = None
lua_lib.NewLuaEngine.restype = ctypes.c_void_p
lua_lib.ExecuteScript.argtypes = [
    ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p,
    ctypes.c_char_p
]
lua_lib.ExecuteScript.restype = ctypes.c_void_p
engine = lua_lib.NewLuaEngine()


class LuaOutput(ctypes.Structure):
  """Python wrapper class around LuaOutput struct as defined in lua_engine.h.
  """
  _fields_ = [('output', ctypes.POINTER(ctypes.c_char_p)),
              ('size', ctypes.c_int), ('saved_state', ctypes.c_char_p)]


@app.route('/')
def index():
  """Renders the main page of the tool.

  Returns:
    A JSON response with a string of the rendered index.html page.
  """
  return flask.render_template('index.html')


@app.route('/get_published_data_file_names_and_content', methods=['POST'])
def get_published_data_file_names_and_content():
  """Returns the list of all the JSON file names under the data
  directory without their file extensions. Also returns the
  JSON of each published data file under the data directory as a string.

  Returns:
    A JSON response with file names under the key "file_names" and each 
    of the published data strings under the key of their file name.
  """
  file_path = os.path.join(os.path.dirname(__file__), "data")
  all_json_files = filter(lambda file: file.endswith('.json'),
                          os.listdir(file_path))
  file_names = list(
      # lamda function leverages splitext, which produces a tuple of
      # the file name and the extension.
      map(lambda file: os.path.splitext(file)[0], all_json_files))

  response = {"file_names": file_names}

  for file_name in file_names:
    json_file_path = os.path.join(file_path, file_name + '.json')
    json_file = open(json_file_path)
    response[file_name] = json.dumps(json.load(json_file), indent=2)
    json_file.close()

  return response


@app.route('/execute_script', methods=['POST'])
def execute_script():
  """Executes the Lua script from the request
  re-rendering the home page with the output.

  Returns:
    A JSON response containing the string of the rendered index.html
    page with output, script, published data, and the saved state specified.
  """
  script = flask.request.form['script']
  function_name = flask.request.form['function-name']
  published_data = flask.request.form['published-data']
  saved_state = flask.request.form['saved-state']
  # ctypes requires that strings are encoded to bytes
  lua_output = LuaOutput.from_address(
      lua_lib.ExecuteScript(engine, script.encode(), function_name.encode(),
                            published_data.encode(), saved_state.encode()))

  # ctypes encodes strings as bytes so they must be decoded back to strings.
  decoded_output = []
  for i in range(lua_output.size):
    decoded_output.append(prettify_json(lua_output.output[i].decode()))

  new_saved_state = prettify_json(
      ctypes.string_at(lua_output.saved_state).decode())
  saved_state = new_saved_state if new_saved_state else saved_state

  lua_lib.FreeLuaOutput(ctypes.byref(lua_output))

  return flask.render_template('index.html',
                               script=script,
                               output=decoded_output,
                               function_name=function_name,
                               published_data=published_data,
                               saved_state=saved_state)


def prettify_json(string):
  """Prettifies the string if it represents a JSON with an indent of 2.

  Args:
    string (str): String to prettify

  Returns:
    A string of the formatted JSON. If the string does not represent a
    JSON, the string is returned back with no changes.
  """
  try:
    # If the string cannot be loaded into a JSON,
    # json.loads throws a JSONDecodeError exception.
    json_object = json.loads(string)
    json_formatted_str = json.dumps(json_object, indent=2)
    return json_formatted_str
  except json.JSONDecodeError as e:
    return string


if __name__ == '__main__':
  app.run()