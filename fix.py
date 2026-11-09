import re

# 1. LaunchActivity
la_path = "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
with open(la_path, "r", encoding="utf-8") as f: la = f.read()

if "onVideoFilePickerResult" not in la:
    match = re.search(r'(public void onActivityResult\(int requestCode, int resultCode, Intent data\)\s*\{)', la)
    if match:
        la = la.replace(match.group(1), match.group(1) + "\n        if (VoIPFragment.getInstance() != null) { VoIPFragment.onVideoFilePickerResult(requestCode, resultCode, data); }")
        print("✅ LaunchActivity: Added hook")
    else:
        print("❌ LaunchActivity: onActivityResult not found")
else:
    print("⚠️ LaunchActivity: already has hook")

with open(la_path, "w", encoding="utf-8") as f: f.write(la)

# 2. VoIPFragment
v_path = "TMessagesProj/src/main/java/org/telegram/ui/VoIPFragment.java"
with open(v_path, "r", encoding="utf-8") as f: v = f.read()

if "fakeCameraButton" not in v:
    if "import android.widget.TextView;" not in v:
        v = v.replace("import android.app.Activity;", "import android.app.Activity;\nimport android.content.Intent;\nimport android.graphics.drawable.GradientDrawable;\nimport android.widget.TextView;")
    
    if "REQUEST_CODE_PICK_VIDEO_FILE" not in v:
        v = v.replace("private final static int STATE_FLOATING = 2;", "private final static int STATE_FLOATING = 2;\n    private static final int REQUEST_CODE_PICK_VIDEO_FILE = 0x0F0F;\n    private FrameLayout fakeCameraButton;")
    
    btn = '''
        fakeCameraButton = new FrameLayout(context);
        GradientDrawable fcbBg = new GradientDrawable();
        fcbBg.setColor(0xCC1E88E5);
        fcbBg.setCornerRadius(AndroidUtilities.dp(20));
        fakeCameraButton.setBackground(fcbBg);
        fakeCameraButton.setClickable(true);
        fakeCameraButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/mp4");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (activity != null) activity.startActivityForResult(intent, REQUEST_CODE_PICK_VIDEO_FILE);
        });
        TextView fcbLabel = new TextView(context);
        fcbLabel.setText("🎬 فيديو");
        fcbLabel.setTextColor(0xFFFFFFFF);
        fcbLabel.setTextSize(14);
        fcbLabel.setGravity(android.view.Gravity.CENTER);
        fakeCameraButton.addView(fcbLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams fcbLp = new FrameLayout.LayoutParams(AndroidUtilities.dp(110), AndroidUtilities.dp(40));
        fcbLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        fcbLp.topMargin = AndroidUtilities.dp(16) + (lastInsets != null ? lastInsets.getSystemWindowInsetTop() : AndroidUtilities.statusBarHeight);
        fcbLp.rightMargin = AndroidUtilities.dp(16);
        fragmentView.addView(fakeCameraButton, fcbLp);
'''
    match = re.search(r'(fragmentView = frameLayout;)', v)
    if match:
        v = v.replace(match.group(1), match.group(1) + btn)
        print("✅ VoIPFragment: Added button")
    else:
        print("❌ VoIPFragment: fragmentView anchor not found")

    method = '''
    public static void onVideoFilePickerResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_PICK_VIDEO_FILE) return;
        VoIPFragment instance = getInstance();
        if (instance == null) return;
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setFileVideoSource(data.getData());
            }
        }
    }
'''
    idx = v.rfind("}")
    v = v[:idx] + method + v[idx:]
    print("✅ VoIPFragment: Added method")

with open(v_path, "w", encoding="utf-8") as f: f.write(v)
