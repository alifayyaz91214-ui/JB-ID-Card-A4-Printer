plugins { id("com.android.application") }
android {
 namespace = "pk.jb.idcardprinter"
 compileSdk = 35
 defaultConfig { applicationId = "pk.jb.idcardprinter"; minSdk = 24; targetSdk = 28; versionCode = 2; versionName = "1.1" }
}

dependencies { implementation("org.opencv:opencv:4.10.0") }
