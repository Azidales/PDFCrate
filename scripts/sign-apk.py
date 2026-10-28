#!/usr/bin/env python3
"""Sign a release with the pinned private key; never generate or substitute a key."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--build-tools', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
expected = (root / 'signing/certificate.sha256').read_text().strip()
if not args.apk.is_file() or not args.keystore.is_file() or not args.password_file.is_file():
    raise SystemExit('APK, private keystore and password file must already exist. No fallback key is generated.')
if args.apk.resolve() == args.output.resolve():
    raise SystemExit('Use a separate output file; the source APK must be preserved.')
cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(args.keystore), '-storetype', 'PKCS12',
    '-storepass:file', str(args.password_file), '-alias', 'pdfcrate'], capture_output=True, check=True).stdout
if hashlib.sha256(cert).hexdigest() != expected:
    raise SystemExit('Signing certificate differs from the pinned PDFCrate identity. Refusing to sign.')
badging = subprocess.run([str(args.build_tools / 'aapt'), 'dump', 'badging', str(args.apk)],
    check=True, capture_output=True, text=True).stdout
if "application-debuggable" in badging:
    raise SystemExit("Debug builds are test-only; provide the unsigned release APK.")
if "name='dev.pdfcrate.app'" not in badging.splitlines()[0]:
    raise SystemExit('Unexpected Android package; refusing to sign.')
args.output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='pdfcrate-sign-') as directory:
    aligned = Path(directory) / 'aligned.apk'
    subprocess.run([str(args.build_tools / 'zipalign'), '-P', '16', '-f', '4', str(args.apk), str(aligned)], check=True)
    subprocess.run([str(args.build_tools / 'apksigner'), 'sign', '--ks', str(args.keystore), '--ks-type', 'PKCS12',
        '--ks-key-alias', 'pdfcrate', '--ks-pass', f'file:{args.password_file}',
        '--min-sdk-version', '26', '--out', str(args.output), str(aligned)], check=True)
verified = subprocess.run([str(args.build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.output)],
    check=True, capture_output=True, text=True).stdout
if f'certificate SHA-256 digest: {expected}' not in verified:
    args.output.unlink(missing_ok=True)
    raise SystemExit('Signed APK identity verification failed.')
subprocess.run([str(args.build_tools / 'zipalign'), '-c', '-P', '16', '4', str(args.output)], check=True)
args.output.with_suffix('.sha256').write_text(hashlib.sha256(args.output.read_bytes()).hexdigest() + '  ' + args.output.name + '\n')
print('Signed APK verified against the pinned PDFCrate certificate. Passwords and private keys were not printed.')
