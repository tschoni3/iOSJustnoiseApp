#!/usr/bin/env python3
"""Create one owner-authorized upload identity outside Git; never print passwords.

Refuses to overwrite an existing destination, including a partial earlier attempt.
The encrypted keystore and password file need an owner-managed secure backup.
"""
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import sys


def main():
    if len(sys.argv) != 2:
        raise ValueError("Usage: create-upload-key.py <new external directory>")
    destination = Path(sys.argv[1]).expanduser().absolute()
    repository = Path(__file__).resolve().parents[1]
    if destination != destination.resolve() or destination.is_relative_to(repository):
        raise ValueError("Choose a real external directory, not a repository path or symlink")
    java = Path(os.environ["JAVA_HOME"]) / "bin/keytool"
    if not java.is_file():
        raise ValueError("Existing JDK keytool required")
    os.umask(0o077)
    destination.mkdir(mode=0o700, parents=True, exist_ok=False)
    password = secrets.token_hex(32)
    environment = {**os.environ, "JUSTNOISE_NEW_KEY_PASSWORD": password}
    store = destination / "upload.p12"
    public = destination / "upload-certificate.der"
    trust = destination / "upload-truststore.p12"
    alias = "justnoise-upload"

    def keytool(*arguments):
        result = subprocess.run([str(java), *arguments], env=environment,
                                stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
        if result.returncode:
            # Do not expose arbitrary signing-tool diagnostics or credentials.
            raise RuntimeError("Signing identity creation failed; preserve the partial directory for inspection")

    # Persist recovery credentials before key creation, with exclusive owner-only IO.
    with (destination / "signing.properties").open("x") as stream:
        stream.write(f"storeFile={store}\nstorePassword={password}\nkeyAlias={alias}\nkeyPassword={password}\n")
    keytool("-genkeypair", "-keystore", str(store), "-storetype", "PKCS12",
            "-storepass:env", "JUSTNOISE_NEW_KEY_PASSWORD", "-keypass:env", "JUSTNOISE_NEW_KEY_PASSWORD",
            "-alias", alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
            "-dname", "CN=Justnoise Upload, O=Justnoise Media", "-noprompt")
    keytool("-exportcert", "-keystore", str(store), "-storepass:env", "JUSTNOISE_NEW_KEY_PASSWORD",
            "-alias", alias, "-file", str(public))
    keytool("-importcert", "-keystore", str(trust), "-storetype", "PKCS12",
            "-storepass:env", "JUSTNOISE_NEW_KEY_PASSWORD", "-alias", alias,
            "-file", str(public), "-noprompt")
    pin = hashlib.sha256(public.read_bytes()).hexdigest()
    (destination / "upload-certificate.sha256").write_text(pin + "\n")
    print("Created encrypted upload key and owner-only signing properties outside the repository.")
    print("Public certificate SHA-256: " + pin)
    print("Secure offline backup remains required before distribution.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, RuntimeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
