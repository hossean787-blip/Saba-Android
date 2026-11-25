package ir.sabagostar.suramanager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Per-use biometric-bound encryption. Password never goes into preferences, logs or backup unencrypted. */
final class ManagerLoginVault {
    static final String ADMIN = "hossean787+suramanager@gmail.com";
    private static final String KEY = "sura_manager_login_aes_v1";
    private static final byte[] AAD = "sura-manager-login-v1".getBytes(StandardCharsets.UTF_8);
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Listener listener;
    private CancellationSignal signal;
    private int generation;

    interface Listener {
        void state(String email, boolean saved, boolean available);
        void unlocked(String email, String password);
        void notice(String message);
    }
    ManagerLoginVault(Activity activity, Listener listener) {
        this.activity=activity; this.listener=listener;
        prefs=activity.getSharedPreferences("manager_secure_login", Activity.MODE_PRIVATE);
    }
    boolean available() {
        if (Build.VERSION.SDK_INT < 28) return false;
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager manager=activity.getSystemService(BiometricManager.class);
            return manager!=null && manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS;
        }
        // Android 9/10: crypto-bound prompt enforces an eligible biometric at authentication time.
        return true;
    }
    private boolean saved() {return prefs.contains("ciphertext") && prefs.contains("iv");}
    void state() {listener.state(prefs.getString("email",""),saved(),available());}
    void rememberEmail(String email) {
        if(email==null || !ADMIN.equalsIgnoreCase(email.trim()))return;
        prefs.edit().putString("email",ADMIN).apply();state();
    }
    private KeyStore store() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);return ks;
    }
    private SecretKey key(boolean create) throws Exception {
        KeyStore ks=store();
        if(ks.containsAlias(KEY)) return (SecretKey)ks.getKey(KEY,null);
        if(!create)throw new IllegalStateException("Missing key");
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec.Builder builder=new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true);
        if(Build.VERSION.SDK_INT>=24)builder.setInvalidatedByBiometricEnrollment(true);
        if(Build.VERSION.SDK_INT>=30)builder.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG);
        else builder.setUserAuthenticationValidityDurationSeconds(-1);
        gen.init(builder.build());return gen.generateKey();
    }
    void cancel(){generation++;if(signal!=null){signal.cancel();signal=null;}}
    private void erase(boolean email) {
        cancel();
        SharedPreferences.Editor editor=prefs.edit().remove("ciphertext").remove("iv");
        if(email)editor.remove("email");
        editor.commit();
        try{store().deleteEntry(KEY);}catch(Exception ignored){}
    }
    void clearPassword(){erase(false);state();}
    void forget(){erase(true);state();listener.notice("اطلاعات ورود ذخیره‌شده حذف شد.");}
    void enroll(String email,String password){
        if(!ADMIN.equalsIgnoreCase(email==null?"":email.trim())||password==null||password.isEmpty()||password.length()>256)return;
        rememberEmail(email);
        if(!available()){listener.notice("اثر انگشت یا احراز هویت زیستی امن در دستگاه فعال نیست؛ ورود دستی برقرار است.");return;}
        if(signal!=null)return;
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            try{cipher.init(Cipher.ENCRYPT_MODE,key(true));}
            catch(KeyPermanentlyInvalidatedException invalid){erase(false);cipher.init(Cipher.ENCRYPT_MODE,key(true));}
            prompt(cipher,true,password);
        }catch(Exception e){listener.notice("فعال‌سازی ذخیره امن انجام نشد؛ ورود دستی را ادامه دهید. [BIO-ENROLL]");state();}
    }
    void unlock(){
        if(signal!=null)return;
        if(!available()||!saved()){listener.notice("ابتدا با رمز وارد شوید و ذخیره امن را فعال کنید.");state();return;}
        try{
            byte[] iv=Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP);
            if(iv.length!=12)throw new IllegalStateException("Invalid IV");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));
            prompt(cipher,false,null);
        }catch(Exception e){erase(false);state();listener.notice("اطلاعات امن دیگر قابل استفاده نیست؛ با رمز وارد شوید و اثر انگشت را دوباره فعال کنید. [BIO-KEY]");}
    }
    @TargetApi(28)
    private void prompt(Cipher cipher,boolean enroll,String password){
        final int attempt=++generation;
        CancellationSignal pending=new CancellationSignal();signal=pending;
        BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity)
            .setTitle(enroll?"فعال‌سازی ورود امن سورا مدیر":"ورود امن سورا مدیر")
            .setSubtitle("با اثر انگشت یا زیست‌سنجی قوی دستگاه تأیید کنید")
            .setNegativeButton("انصراف",activity.getMainExecutor(),(d,w)->{
                if(attempt==generation){cancel();state();}
            });
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        try {
            builder.build().authenticate(new BiometricPrompt.CryptoObject(cipher),pending,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationError(int code,CharSequence message){
                    if(attempt!=generation)return;
                    signal=null;state();
                    listener.notice("احراز هویت زیستی انجام نشد؛ از رمز عبور استفاده کنید. [BIO-"+code+"]");
                }
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                    if(attempt!=generation||activity.isFinishing()||activity.isDestroyed())return;
                    signal=null;
                    byte[] plain=null;
                    try{
                        Cipher unlocked=result.getCryptoObject()==null?null:result.getCryptoObject().getCipher();
                        if(unlocked==null)throw new IllegalStateException("Missing crypto authorization");
                        unlocked.updateAAD(AAD);
                        if(enroll){
                            plain=new JSONObject().put("email",ADMIN).put("password",password).toString().getBytes(StandardCharsets.UTF_8);
                            byte[] encrypted=unlocked.doFinal(plain);
                            if(!prefs.edit().putString("email",ADMIN).putString("ciphertext",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(unlocked.getIV(),Base64.NO_WRAP)).commit())
                                throw new IllegalStateException("Storage failure");
                            state();listener.notice("ورود با اثر انگشت فعال شد.");
                        }else{
                            byte[] encrypted=Base64.decode(prefs.getString("ciphertext",""),Base64.NO_WRAP);
                            if(encrypted.length<16||encrypted.length>4096)throw new IllegalStateException("Invalid ciphertext");
                            plain=unlocked.doFinal(encrypted);
                            JSONObject login=new JSONObject(new String(plain,StandardCharsets.UTF_8));
                            if(!ADMIN.equals(login.optString("email"))||login.optString("password").isEmpty())throw new IllegalStateException("Invalid credential");
                            listener.unlocked(ADMIN,login.getString("password"));
                        }
                    }catch(Exception e){erase(false);state();listener.notice("ذخیره یا بازگشایی امن انجام نشد؛ ورود دستی را انجام دهید. [BIO-CRYPTO]");}
                    finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
                }
            });
        }catch(RuntimeException e){cancel();state();listener.notice("اثر انگشت دستگاه در دسترس نیست. [BIO-PROMPT]");}
    }
}
