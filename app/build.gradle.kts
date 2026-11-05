plugins { id("com.android.application") }
android {
 namespace = "pk.jb.idcardprinter"
 compileSdk = 35
 defaultConfig { applicationId = "pk.jb.idcardprinter"; minSdk = 24; targetSdk = 28; versionCode = 1; versionName = "1.0" }
}

dependencies { implementation("org.opencv:opencv:4.10.0") }
