-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature
-keep public class com.kimrosil.krlivestudio.MainActivity {
    public <init>();
    protected void on*(...);
    public void on*(...);
}
-keep public class com.kimrosil.krlivestudio.MainActivity$StreamService {
    public <init>();
    public void onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public android.os.IBinder onBind(android.content.Intent);
    public void onDestroy();
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
