#!/usr/bin/env python3

import sys
import os

LIBRARY = "libusb-1.0"

# Mock data for libusb-1.0
MOCK_CONFIG = {
    "cflags": f"-I{os.getcwd()}/src/main/cpp/jni/libusb/libusb",
    "libs": "-lusb-1.0",
    "modversion": "1.0.26",
    "version": "1.0",
    "exists": True
}

def main():
    args = sys.argv[1:]

    if not args or (LIBRARY not in args and "--version" not in args):
        print(f"Package {LIBRARY} was not found", file=sys.stderr)
        sys.exit(1)

    # Remove the library name from the argument list
    args = [arg for arg in args if arg != LIBRARY]

    for arg in args:
        if arg == "--cflags":
            print(MOCK_CONFIG["cflags"])
        elif arg == "--cflags-only-I":
            print(MOCK_CONFIG["cflags"])
        elif arg == "--cflags-only-other":
            print("")
        elif arg == "--libs":
            print(MOCK_CONFIG["libs"])
        elif arg == "--modversion":
            print(MOCK_CONFIG["modversion"])
        elif arg == "--exists":
            # If exists is True, do nothing; if False, return error
            if not MOCK_CONFIG["exists"]:
                sys.exit(1)
        elif arg == "--version":
            print(MOCK_CONFIG["version"])
        else:
            pass
            # print(f"Unsupported option: {arg}", file=sys.stderr)
            # sys.exit(1)

if __name__ == "__main__":
    print("PKGCONFIG", file=sys.stderr)
    print(f"{sys.argv}", file=sys.stderr)
    main()