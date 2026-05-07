package com.crystalize.upscaler;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private View dropZone, previewSection, progressCard;
    private ImageView imagePreview;
    private TextView tabOriginal, tabUpscaled, badgeSize, badgeEnhanced;
    private TextView statW, statH, statScale;
    private TextView scale2x, scale3x, scale4x;
    private TextView sharpVal, denoiseVal, detailVal;
    private SeekBar sharpSlider, denoiseSlider, detailSlider;
    private View upscaleBtn, downloadBtn, resetLink;
    private ProgressBar progressBar;
    private TextView progressPct, progressStep;

    private Bitmap originalBitmap = null;
    private Bitmap upscaledBitmap = null;
    private int selectedScale = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) loadImageFromUri(uri);
        });

    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            imagePickerLauncher.launch("image/*");
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupListeners();

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) loadImageFromUri(imageUri);
        }
    }

    private void bindViews() {
        dropZone = findViewById(R.id.dropZone);
        previewSection = findViewById(R.id.previewSection);
        progressCard = findViewById(R.id.progressCard);
        imagePreview = findViewById(R.id.imagePreview);
        tabOriginal = findViewById(R.id.tabOriginal);
        tabUpscaled = findViewById(R.id.tabUpscaled);
        badgeSize = findViewById(R.id.badgeSize);
        badgeEnhanced = findViewById(R.id.badgeEnhanced);
        statW = findViewById(R.id.statW);
        statH = findViewById(R.id.statH);
        statScale = findViewById(R.id.statScale);
        scale2x = findViewById(R.id.scale2x);
        scale3x = findViewById(R.id.scale3x);
        scale4x = findViewById(R.id.scale4x);
        sharpVal = findViewById(R.id.sharpVal);
        denoiseVal = findViewById(R.id.denoiseVal);
        detailVal = findViewById(R.id.detailVal);
        sharpSlider = findViewById(R.id.sharpSlider);
        denoiseSlider = findViewById(R.id.denoiseSlider);
        detailSlider = findViewById(R.id.detailSlider);
        upscaleBtn = findViewById(R.id.upscaleBtn);
        downloadBtn = findViewById(R.id.downloadBtn);
        resetLink = findViewById(R.id.resetLink);
        progressBar = findViewById(R.id.progressBar);
        progressPct = findViewById(R.id.progressPct);
        progressStep = findViewById(R.id.progressStep);
    }

    private void setupListeners() {
        dropZone.setOnClickListener(v -> pickImage());
        tabOriginal.setOnClickListener(v -> switchTab(false));
        tabUpscaled.setOnClickListener(v -> { if (upscaledBitmap != null) switchTab(true); });
        scale2x.setOnClickListener(v -> setScale(2));
        scale3x.setOnClickListener(v -> setScale(3));
        scale4x.setOnClickListener(v -> setScale(4));
        sharpSlider.setOnSeekBarChangeListener(new SimpleSeekListener(sharpVal));
        denoiseSlider.setOnSeekBarChangeListener(new SimpleSeekListener(denoiseVal));
        detailSlider.setOnSeekBarChangeListener(new SimpleSeekListener(detailVal));
        upscaleBtn.setOnClickListener(v -> startUpscale());
        downloadBtn.setOnClickListener(v -> downloadImage());
        resetLink.setOnClickListener(v -> resetApp());
    }

    private void pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES});
                return;
            }
        }
        imagePickerLauncher.launch("image/*");
    }

    private void loadImageFromUri(Uri uri) {
        executor.execute(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
                int maxDim = 2048;
                int sample = 1;
                while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2;
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
                if (bmp == null) { mainHandler.post(() -> showToast("Failed to load image")); return; }
                originalBitmap = bmp;
                mainHandler.post(() -> {
                    imagePreview.setImageBitmap(originalBitmap);
                    statW.setText(String.valueOf(originalBitmap.getWidth()));
                    statH.setText(String.valueOf(originalBitmap.getHeight()));
                    statScale.setText(selectedScale + "×");
                    badgeSize.setText(originalBitmap.getWidth() + "×" + originalBitmap.getHeight());
                    dropZone.setVisibility(View.GONE);
                    previewSection.setVisibility(View.VISIBLE);
                    downloadBtn.setVisibility(View.GONE);
                    tabUpscaled.setAlpha(0.5f);
                    upscaledBitmap = null;
                    switchTab(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> showToast("Error: " + e.getMessage()));
            }
        });
    }

    private void setScale(int scale) {
        selectedScale = scale;
        statScale.setText(scale + "×");
        int white = getColor(R.color.white);
        int textSec = getColor(R.color.text_secondary);
        scale2x.setBackgroundResource(scale == 2 ? R.drawable.bg_scale_active : R.drawable.bg_scale_inactive);
        scale2x.setTextColor(scale == 2 ? white : textSec);
        scale3x.setBackgroundResource(scale == 3 ? R.drawable.bg_scale_active : R.drawable.bg_scale_inactive);
        scale3x.setTextColor(scale == 3 ? white : textSec);
        scale4x.setBackgroundResource(scale == 4 ? R.drawable.bg_scale_active : R.drawable.bg_scale_inactive);
        scale4x.setTextColor(scale == 4 ? white : textSec);
    }

    private void switchTab(boolean upscaled) {
        if (upscaled && upscaledBitmap != null) {
            imagePreview.setImageBitmap(upscaledBitmap);
            badgeSize.setText(upscaledBitmap.getWidth() + "×" + upscaledBitmap.getHeight());
            badgeEnhanced.setVisibility(View.VISIBLE);
            tabOriginal.setBackgroundResource(R.drawable.bg_tab_inactive);
            tabOriginal.setTextColor(getColor(R.color.text_tertiary));
            tabUpscaled.setBackgroundResource(R.drawable.bg_tab_active);
            tabUpscaled.setTextColor(getColor(R.color.accent));
        } else {
            if (originalBitmap != null) {
                imagePreview.setImageBitmap(originalBitmap);
                badgeSize.setText(originalBitmap.getWidth() + "×" + originalBitmap.getHeight());
            }
            badgeEnhanced.setVisibility(View.GONE);
            tabOriginal.setBackgroundResource(R.drawable.bg_tab_active);
            tabOriginal.setTextColor(getColor(R.color.accent));
            tabUpscaled.setBackgroundResource(R.drawable.bg_tab_inactive);
            tabUpscaled.setTextColor(getColor(R.color.text_tertiary));
        }
    }

    private void startUpscale() {
        if (originalBitmap == null) return;
        upscaleBtn.setEnabled(false);
        progressCard.setVisibility(View.VISIBLE);
        downloadBtn.setVisibility(View.GONE);

        int sharpness = sharpSlider.getProgress();
        int denoise = denoiseSlider.getProgress();
        int detail = detailSlider.getProgress();

        String[][] steps = {
            {"10","Analyzing image…"},{"25","Running super-resolution…"},
            {"50","Enhancing edges…"},{"70","Sharpening…"},
            {"85","Denoising…"},{"95","Finalizing…"}
        };

        executor.execute(() -> {
            for (String[] step : steps) {
                try { Thread.sleep(250); } catch (InterruptedException e) { break; }
                int pct = Integer.parseInt(step[0]);
                String label = step[1];
                mainHandler.post(() -> {
                    progressBar.setProgress(pct);
                    progressPct.setText(pct + "%");
                    progressStep.setText(label);
                });
            }

            Bitmap result = performUpscale(originalBitmap, selectedScale, sharpness, denoise, detail);

            mainHandler.post(() -> {
                progressBar.setProgress(100);
                progressPct.setText("100%");
                progressStep.setText("Done!");
                mainHandler.postDelayed(() -> {
                    upscaledBitmap = result;
                    progressCard.setVisibility(View.GONE);
                    upscaleBtn.setEnabled(true);
                    downloadBtn.setVisibility(View.VISIBLE);
                    tabUpscaled.setAlpha(1f);
                    switchTab(true);
                    showToast("✦ Upscaled successfully!");
                }, 400);
            });
        });
    }

    private Bitmap performUpscale(Bitmap src, int scale, int sharpness, int denoise, int detail) {
        int dstW = src.getWidth() * scale, dstH = src.getHeight() * scale;
        Bitmap upscaled = Bitmap.createScaledBitmap(src, dstW, dstH, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[dstW * dstH];
        upscaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH);
        if (denoise > 1) pixels = applyDenoise(pixels, dstW, dstH, denoise);
        if (sharpness > 1) pixels = applyUnsharpMask(pixels, dstW, dstH, sharpness, detail);
        upscaled.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH);
        Bitmap enhanced = applyColorEnhancement(upscaled, detail / 10f);
        if (enhanced != upscaled) upscaled.recycle();
        return enhanced;
    }

    private int[] applyDenoise(int[] pixels, int w, int h, int strength) {
        int[] out = pixels.clone();
        int r = Math.max(1, strength / 3);
        float blend = strength * 0.025f;
        for (int y = r; y < h - r; y++) {
            for (int x = r; x < w - r; x++) {
                int rS=0,gS=0,bS=0,cnt=0;
                for (int dy=-r;dy<=r;dy++) for (int dx=-r;dx<=r;dx++) {
                    int p=pixels[(y+dy)*w+(x+dx)];
                    rS+=(p>>16)&0xFF; gS+=(p>>8)&0xFF; bS+=p&0xFF; cnt++;
                }
                int o=pixels[y*w+x];
                int nR=clamp((int)(((o>>16)&0xFF)*(1-blend)+(rS/cnt)*blend));
                int nG=clamp((int)(((o>>8)&0xFF)*(1-blend)+(gS/cnt)*blend));
                int nB=clamp((int)((o&0xFF)*(1-blend)+(bS/cnt)*blend));
                out[y*w+x]=0xFF000000|(nR<<16)|(nG<<8)|nB;
            }
        }
        return out;
    }

    private int[] applyUnsharpMask(int[] pixels, int w, int h, int sharpness, int detail) {
        int[] blurred = pixels.clone();
        int r=2;
        for (int y=r;y<h-r;y++) for (int x=r;x<w-r;x++) {
            int rS=0,gS=0,bS=0,cnt=0;
            for (int dy=-r;dy<=r;dy++) for (int dx=-r;dx<=r;dx++) {
                int p=pixels[(y+dy)*w+(x+dx)];
                rS+=(p>>16)&0xFF; gS+=(p>>8)&0xFF; bS+=p&0xFF; cnt++;
            }
            blurred[y*w+x]=0xFF000000|((rS/cnt)<<16)|((gS/cnt)<<8)|(bS/cnt);
        }
        int[] out=pixels.clone();
        float amount=sharpness*0.15f+detail*0.06f;
        for (int i=0;i<pixels.length;i++) {
            int p=pixels[i],b=blurred[i];
            out[i]=0xFF000000|
                (clamp((int)(((p>>16)&0xFF)+(((p>>16)&0xFF)-((b>>16)&0xFF))*amount))<<16)|
                (clamp((int)(((p>>8)&0xFF)+(((p>>8)&0xFF)-((b>>8)&0xFF))*amount))<<8)|
                clamp((int)((p&0xFF)+((p&0xFF)-(b&0xFF))*amount));
        }
        return out;
    }

    private Bitmap applyColorEnhancement(Bitmap src, float strength) {
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        float contrast = 1f + strength * 0.15f;
        float translate = (-0.5f * contrast + 0.5f) * 255f;
        ColorMatrix cm = new ColorMatrix();
        ColorMatrix sat = new ColorMatrix(); sat.setSaturation(1f + strength * 0.12f);
        ColorMatrix con = new ColorMatrix(new float[]{
            contrast,0,0,0,translate, 0,contrast,0,0,translate,
            0,0,contrast,0,translate, 0,0,0,1,0});
        cm.set(sat); cm.postConcat(con);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return result;
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private void downloadImage() {
        if (upscaledBitmap == null) return;
        executor.execute(() -> {
            try {
                String filename = "Crystalize_" + selectedScale + "x_" + System.currentTimeMillis() + ".png";
                OutputStream out;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Crystalize");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    out = getContentResolver().openOutputStream(uri);
                } else {
                    java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Crystalize");
                    dir.mkdirs();
                    out = new java.io.FileOutputStream(new java.io.File(dir, filename));
                }
                upscaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush(); out.close();
                mainHandler.post(() -> showToast("Saved to Pictures/Crystalize!"));
            } catch (IOException e) {
                mainHandler.post(() -> showToast("Save failed: " + e.getMessage()));
            }
        });
    }

    private void resetApp() {
        dropZone.setVisibility(View.VISIBLE);
        previewSection.setVisibility(View.GONE);
        if (originalBitmap != null) { originalBitmap.recycle(); originalBitmap = null; }
        if (upscaledBitmap != null) { upscaledBitmap.recycle(); upscaledBitmap = null; }
        downloadBtn.setVisibility(View.GONE);
        progressCard.setVisibility(View.GONE);
    }

    private void showToast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onDestroy() { super.onDestroy(); executor.shutdown(); }

    static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final TextView label;
        SimpleSeekListener(TextView label) { this.label = label; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean f) { label.setText(String.valueOf(p)); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
