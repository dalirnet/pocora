# ML Kit starts its parts by name, from the manifest. Without their constructors the scanner is never set up
# and BarcodeScanning.getClient crashes.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
