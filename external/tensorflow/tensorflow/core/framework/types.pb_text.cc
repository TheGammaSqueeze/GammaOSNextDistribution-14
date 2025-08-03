// GENERATED FILE - DO NOT MODIFY

#include <algorithm>

#include "tensorflow/core/framework/types.pb_text-impl.h"

using ::tensorflow::strings::ProtoSpaceAndComments;
using ::tensorflow::strings::Scanner;
using ::tensorflow::strings::StrCat;

namespace tensorflow {

const char* EnumName_DataType(
    ::tensorflow::DataType value) {
  switch (value) {
    case 0: return "DT_INVALID";
    case 1: return "DT_FLOAT";
    case 2: return "DT_DOUBLE";
    case 3: return "DT_INT32";
    case 4: return "DT_UINT8";
    case 5: return "DT_INT16";
    case 6: return "DT_INT8";
    case 7: return "DT_STRING";
    case 8: return "DT_COMPLEX64";
    case 9: return "DT_INT64";
    case 10: return "DT_BOOL";
    case 11: return "DT_QINT8";
    case 12: return "DT_QUINT8";
    case 13: return "DT_QINT32";
    case 14: return "DT_BFLOAT16";
    case 15: return "DT_QINT16";
    case 16: return "DT_QUINT16";
    case 17: return "DT_UINT16";
    case 18: return "DT_COMPLEX128";
    case 19: return "DT_HALF";
    case 20: return "DT_RESOURCE";
    case 21: return "DT_VARIANT";
    case 22: return "DT_UINT32";
    case 23: return "DT_UINT64";
    case 101: return "DT_FLOAT_REF";
    case 102: return "DT_DOUBLE_REF";
    case 103: return "DT_INT32_REF";
    case 104: return "DT_UINT8_REF";
    case 105: return "DT_INT16_REF";
    case 106: return "DT_INT8_REF";
    case 107: return "DT_STRING_REF";
    case 108: return "DT_COMPLEX64_REF";
    case 109: return "DT_INT64_REF";
    case 110: return "DT_BOOL_REF";
    case 111: return "DT_QINT8_REF";
    case 112: return "DT_QUINT8_REF";
    case 113: return "DT_QINT32_REF";
    case 114: return "DT_BFLOAT16_REF";
    case 115: return "DT_QINT16_REF";
    case 116: return "DT_QUINT16_REF";
    case 117: return "DT_UINT16_REF";
    case 118: return "DT_COMPLEX128_REF";
    case 119: return "DT_HALF_REF";
    case 120: return "DT_RESOURCE_REF";
    case 121: return "DT_VARIANT_REF";
    case 122: return "DT_UINT32_REF";
    case 123: return "DT_UINT64_REF";
    default: return "";
  }
}

string ProtoDebugString(
    const ::tensorflow::SerializedDType& msg) {
  string s;
  ::tensorflow::strings::ProtoTextOutput o(&s, false);
  internal::AppendProtoDebugString(&o, msg);
  o.CloseTopMessage();
  return s;
}

string ProtoShortDebugString(
    const ::tensorflow::SerializedDType& msg) {
  string s;
  ::tensorflow::strings::ProtoTextOutput o(&s, true);
  internal::AppendProtoDebugString(&o, msg);
  o.CloseTopMessage();
  return s;
}

namespace internal {

void AppendProtoDebugString(
    ::tensorflow::strings::ProtoTextOutput* o,
    const ::tensorflow::SerializedDType& msg) {
  if (msg.datatype() != 0) {
    const char* enum_name = ::tensorflow::EnumName_DataType(msg.datatype());
    if (enum_name[0]) {
      o->AppendEnumName("datatype", enum_name);
    } else {
      o->AppendNumeric("datatype", msg.datatype());
    }
  }
}

}  // namespace internal

bool ProtoParseFromString(
    const string& s,
    ::tensorflow::SerializedDType* msg) {
  msg->Clear();
  Scanner scanner(s);
  if (!internal::ProtoParseFromScanner(&scanner, false, false, msg)) return false;
  scanner.Eos();
  return scanner.GetResult();
}

namespace internal {

bool ProtoParseFromScanner(
    ::tensorflow::strings::Scanner* scanner, bool nested, bool close_curly,
    ::tensorflow::SerializedDType* msg) {
  std::vector<bool> has_seen(1, false);
  while(true) {
    ProtoSpaceAndComments(scanner);
    if (nested && (scanner->Peek() == (close_curly ? '}' : '>'))) {
      scanner->One(Scanner::ALL);
      ProtoSpaceAndComments(scanner);
      return true;
    }
    if (!nested && scanner->empty()) { return true; }
    scanner->RestartCapture()
        .Many(Scanner::LETTER_DIGIT_UNDERSCORE)
        .StopCapture();
    StringPiece identifier;
    if (!scanner->GetResult(nullptr, &identifier)) return false;
    bool parsed_colon = false;
    (void)parsed_colon;
    ProtoSpaceAndComments(scanner);
    if (scanner->Peek() == ':') {
      parsed_colon = true;
      scanner->One(Scanner::ALL);
      ProtoSpaceAndComments(scanner);
    }
    if (identifier == "datatype") {
      if (has_seen[0]) return false;
      has_seen[0] = true;
      StringPiece value;
      if (!parsed_colon || !scanner->RestartCapture().Many(Scanner::LETTER_DIGIT_DASH_UNDERSCORE).GetResult(nullptr, &value)) return false;
      if (value == "DT_INVALID") {
        msg->set_datatype(::tensorflow::DT_INVALID);
      } else if (value == "DT_FLOAT") {
        msg->set_datatype(::tensorflow::DT_FLOAT);
      } else if (value == "DT_DOUBLE") {
        msg->set_datatype(::tensorflow::DT_DOUBLE);
      } else if (value == "DT_INT32") {
        msg->set_datatype(::tensorflow::DT_INT32);
      } else if (value == "DT_UINT8") {
        msg->set_datatype(::tensorflow::DT_UINT8);
      } else if (value == "DT_INT16") {
        msg->set_datatype(::tensorflow::DT_INT16);
      } else if (value == "DT_INT8") {
        msg->set_datatype(::tensorflow::DT_INT8);
      } else if (value == "DT_STRING") {
        msg->set_datatype(::tensorflow::DT_STRING);
      } else if (value == "DT_COMPLEX64") {
        msg->set_datatype(::tensorflow::DT_COMPLEX64);
      } else if (value == "DT_INT64") {
        msg->set_datatype(::tensorflow::DT_INT64);
      } else if (value == "DT_BOOL") {
        msg->set_datatype(::tensorflow::DT_BOOL);
      } else if (value == "DT_QINT8") {
        msg->set_datatype(::tensorflow::DT_QINT8);
      } else if (value == "DT_QUINT8") {
        msg->set_datatype(::tensorflow::DT_QUINT8);
      } else if (value == "DT_QINT32") {
        msg->set_datatype(::tensorflow::DT_QINT32);
      } else if (value == "DT_BFLOAT16") {
        msg->set_datatype(::tensorflow::DT_BFLOAT16);
      } else if (value == "DT_QINT16") {
        msg->set_datatype(::tensorflow::DT_QINT16);
      } else if (value == "DT_QUINT16") {
        msg->set_datatype(::tensorflow::DT_QUINT16);
      } else if (value == "DT_UINT16") {
        msg->set_datatype(::tensorflow::DT_UINT16);
      } else if (value == "DT_COMPLEX128") {
        msg->set_datatype(::tensorflow::DT_COMPLEX128);
      } else if (value == "DT_HALF") {
        msg->set_datatype(::tensorflow::DT_HALF);
      } else if (value == "DT_RESOURCE") {
        msg->set_datatype(::tensorflow::DT_RESOURCE);
      } else if (value == "DT_VARIANT") {
        msg->set_datatype(::tensorflow::DT_VARIANT);
      } else if (value == "DT_UINT32") {
        msg->set_datatype(::tensorflow::DT_UINT32);
      } else if (value == "DT_UINT64") {
        msg->set_datatype(::tensorflow::DT_UINT64);
      } else if (value == "DT_FLOAT_REF") {
        msg->set_datatype(::tensorflow::DT_FLOAT_REF);
      } else if (value == "DT_DOUBLE_REF") {
        msg->set_datatype(::tensorflow::DT_DOUBLE_REF);
      } else if (value == "DT_INT32_REF") {
        msg->set_datatype(::tensorflow::DT_INT32_REF);
      } else if (value == "DT_UINT8_REF") {
        msg->set_datatype(::tensorflow::DT_UINT8_REF);
      } else if (value == "DT_INT16_REF") {
        msg->set_datatype(::tensorflow::DT_INT16_REF);
      } else if (value == "DT_INT8_REF") {
        msg->set_datatype(::tensorflow::DT_INT8_REF);
      } else if (value == "DT_STRING_REF") {
        msg->set_datatype(::tensorflow::DT_STRING_REF);
      } else if (value == "DT_COMPLEX64_REF") {
        msg->set_datatype(::tensorflow::DT_COMPLEX64_REF);
      } else if (value == "DT_INT64_REF") {
        msg->set_datatype(::tensorflow::DT_INT64_REF);
      } else if (value == "DT_BOOL_REF") {
        msg->set_datatype(::tensorflow::DT_BOOL_REF);
      } else if (value == "DT_QINT8_REF") {
        msg->set_datatype(::tensorflow::DT_QINT8_REF);
      } else if (value == "DT_QUINT8_REF") {
        msg->set_datatype(::tensorflow::DT_QUINT8_REF);
      } else if (value == "DT_QINT32_REF") {
        msg->set_datatype(::tensorflow::DT_QINT32_REF);
      } else if (value == "DT_BFLOAT16_REF") {
        msg->set_datatype(::tensorflow::DT_BFLOAT16_REF);
      } else if (value == "DT_QINT16_REF") {
        msg->set_datatype(::tensorflow::DT_QINT16_REF);
      } else if (value == "DT_QUINT16_REF") {
        msg->set_datatype(::tensorflow::DT_QUINT16_REF);
      } else if (value == "DT_UINT16_REF") {
        msg->set_datatype(::tensorflow::DT_UINT16_REF);
      } else if (value == "DT_COMPLEX128_REF") {
        msg->set_datatype(::tensorflow::DT_COMPLEX128_REF);
      } else if (value == "DT_HALF_REF") {
        msg->set_datatype(::tensorflow::DT_HALF_REF);
      } else if (value == "DT_RESOURCE_REF") {
        msg->set_datatype(::tensorflow::DT_RESOURCE_REF);
      } else if (value == "DT_VARIANT_REF") {
        msg->set_datatype(::tensorflow::DT_VARIANT_REF);
      } else if (value == "DT_UINT32_REF") {
        msg->set_datatype(::tensorflow::DT_UINT32_REF);
      } else if (value == "DT_UINT64_REF") {
        msg->set_datatype(::tensorflow::DT_UINT64_REF);
      } else {
        int32 int_value;
        if (strings::SafeStringToNumeric(value, &int_value)) {
          msg->set_datatype(static_cast<::tensorflow::DataType>(int_value));
        } else {
          return false;
        }
      }
    }
  }
}

}  // namespace internal

}  // namespace tensorflow