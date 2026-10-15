package com.mathsmart.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final float DESIGN_W = 360f;
    private static final float DESIGN_H = 760f;

    private FrameLayout root;
    private SmartView view;
    private EditText answer;
    private CountDownTimer timer;
    private SharedPreferences prefs;

    // 0 = menu, 1 = quiz, 2 = settings, 3 = history, 4 = time over, 5 = leaderboard
    private int screen = 0;
    private int previousScreen = 0;
    private final Random rng = new Random();

    private int score = 0, correct = 0, attempted = 0, questionNumber = 0;
    private long timeLeftMs = 45000;
    private int a = 1, b = 1, ans = 2;
    private String opSymbol = "+";
    private String questionText = "";
    private String feedback = "Think about it!";
    private int feedbackColor;
    private boolean locked = false;
    private boolean sessionFinished = false;
    private int bestScore = 0;
    private int currentStreak = 0;

    // Settings
    private String difficulty = "Medium";
    private boolean add = true, sub = false, mul = false, div = false;
    private int timerSeconds = 45;
    private boolean soundEnabled = true;
    private String problemType = "Word Problems"; // "Word Problems", "Equations", "Mixed"

    // Pending settings keep unsaved changes from affecting gameplay.
    private String pendingDifficulty = "Medium";
    private boolean pendingAdd = true, pendingSub = false, pendingMul = false, pendingDiv = false;
    private int pendingTimerSeconds = 45;
    private boolean pendingSoundEnabled = true;
    private String pendingProblemType = "Word Problems";

    // Particles for celebration / sparkles effect
    private static class Particle {
        float x, y, vx, vy, size;
        int color;
        float alpha = 1.0f;

        Particle(float x, float y, float vx, float vy, float size, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.color = color;
        }
    }
    private final List<Particle> particles = new ArrayList<>();

    // Leaderboard Item Data Structure
    private static class LeaderboardItem {
        int score;
        int accuracy;
        String difficulty;
        String date;

        LeaderboardItem(int score, int accuracy, String difficulty, String date) {
            this.score = score;
            this.accuracy = accuracy;
            this.difficulty = difficulty;
            this.date = date;
        }
    }

    // Girly MathSmart palette.
    private static final int BG = Color.rgb(42, 22, 61);
    private static final int BG2 = Color.rgb(63, 32, 84);
    private static final int BG3 = Color.rgb(52, 27, 72);
    private static final int PINK = Color.rgb(231, 123, 167);
    private static final int PINK_DARK = Color.rgb(202, 83, 133);
    private static final int ROSE = Color.rgb(255, 188, 212);
    private static final int LAVENDER = Color.rgb(201, 186, 246);
    private static final int CREAM = Color.rgb(255, 250, 252);
    private static final int WHITE = Color.rgb(255, 252, 255);
    private static final int MUTED = Color.rgb(234, 219, 239);
    private static final int GREEN = Color.rgb(91, 225, 143);
    private static final int RED = Color.rgb(255, 111, 141);
    private static final int GOLD = Color.rgb(247, 190, 103);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        getWindow().setStatusBarColor(Color.rgb(255,247,252));
        getWindow().setNavigationBarColor(Color.rgb(255,247,252));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        prefs = getSharedPreferences("mathsmart", MODE_PRIVATE);
        loadSettings();
        bestScore = prefs.getInt("best_score", 0);
        loadStreakDisplayOnly();
        feedbackColor = MUTED;

        root = new FrameLayout(this);
        view = new SmartView(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        setupAnswer();

        // Keep the 360x760 design clear of Android 15 system bars on devices that report insets.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 23) {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            v.post(this::positionAnswer);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
    }

    private void setupAnswer() {
        answer = new EditText(this);
        answer.setSingleLine(true);
        answer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (Build.VERSION.SDK_INT >= 21) {
            answer.setShowSoftInputOnFocus(false);
        }
        answer.setImeOptions(EditorInfo.IME_ACTION_DONE);
        answer.setTextColor(WHITE);
        answer.setTextSize(19);
        answer.setGravity(Gravity.CENTER);
        answer.setHint("Your answer");
        answer.setHintTextColor(Color.argb(150, 255, 245, 252));
        answer.setBackground(roundDrawable(Color.argb(100,255,255,255), 2, Color.argb(80,255,255,255), 6));
        answer.setPadding(10,0,10,0);
        answer.setVisibility(View.GONE);
        answer.setSelectAllOnFocus(false);
        answer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { checkAnswer(); return true; }
            return false;
        });
        root.addView(answer);
        root.post(this::positionAnswer);
    }

    private GradientDrawable roundDrawable(int fill, int stroke, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void positionAnswer() {
        if (answer == null || view == null) return;
        float sc = view.scale();
        float ox = view.offsetX();
        int boxW = Math.round(202 * sc);
        int boxH = Math.round(44 * sc);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(boxW, boxH);
        lp.leftMargin = Math.round(79 * sc + ox);
        lp.topMargin = Math.round(202 * sc + view.offsetY());
        answer.setLayoutParams(lp);
    }

    private void showAnswer() {
        answer.setVisibility(View.VISIBLE);
        answer.clearFocus();
    }

    private void hideAnswer() {
        answer.setVisibility(View.GONE);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(answer.getWindowToken(), 0);
        answer.clearFocus();
    }

    // Programmatic Sound Effects Synthesizer
    private void playTone(final int[] frequencies, final int[] durationsMs) {
        if (!soundEnabled) return;
        new Thread(() -> {
            try {
                int sampleRate = 22050;
                int totalMs = 0;
                for (int d : durationsMs) totalMs += d;
                int totalSamples = (sampleRate * totalMs) / 1000;
                short[] buffer = new short[totalSamples];
                int currentSample = 0;
                for (int i = 0; i < frequencies.length; i++) {
                    int freq = frequencies[i];
                    int samplesForNote = (sampleRate * durationsMs[i]) / 1000;
                    for (int s = 0; s < samplesForNote; s++) {
                        double t = (double) s / sampleRate;
                        double angle = 2.0 * Math.PI * freq * t;
                        double envelope = 1.0;
                        if (s < 100) envelope = s / 100.0;
                        else if (s > samplesForNote - 200) envelope = (samplesForNote - s) / 200.0;
                        buffer[currentSample++] = (short) (Math.sin(angle) * 16000 * envelope);
                    }
                }
                AudioTrack track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buffer.length * 2,
                        AudioTrack.MODE_STATIC
                );
                track.write(buffer, 0, buffer.length);
                track.play();
                try { Thread.sleep(totalMs + 30); } catch (Exception ignored) {}
                track.release();
            } catch (Exception ignored) {}
        }).start();
    }

    private void playCorrectSound() { playTone(new int[]{523, 659, 784}, new int[]{100, 100, 180}); }
    private void playWrongSound() { playTone(new int[]{220, 180}, new int[]{120, 180}); }
    private void playTimeUpSound() { playTone(new int[]{440, 349, 293}, new int[]{150, 150, 250}); }
    private void playClickSound() { playTone(new int[]{800}, new int[]{25}); }

    private int getAccuracy() {
        return attempted > 0 ? Math.round((correct * 100f) / attempted) : 0;
    }

    // STUDY STREAK CALCULATOR
    private void loadStreakDisplayOnly() {
        currentStreak = prefs.getInt("study_streak", 0);
        if (currentStreak == 0) currentStreak = 1;
    }

    private void updateStreakOnPractice() {
        String lastDateStr = prefs.getString("last_practice_date", "");
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        if (lastDateStr.equals(todayStr)) {
            currentStreak = prefs.getInt("study_streak", 1);
            return;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date today = sdf.parse(todayStr);
            if (!lastDateStr.isEmpty()) {
                Date lastDate = sdf.parse(lastDateStr);
                long diffMs = today.getTime() - lastDate.getTime();
                long diffDays = diffMs / (1000 * 60 * 60 * 24);

                if (diffDays == 1) {
                    currentStreak = prefs.getInt("study_streak", 0) + 1;
                } else {
                    currentStreak = 1;
                }
            } else {
                currentStreak = 1;
            }
        } catch (Exception e) {
            currentStreak = 1;
        }

        prefs.edit()
                .putString("last_practice_date", todayStr)
                .putInt("study_streak", currentStreak)
                .apply();
    }

    // LEADERBOARD STORAGE & RANKING LOGIC
    private List<LeaderboardItem> getLeaderboard() {
        List<LeaderboardItem> items = new ArrayList<>();
        String raw = prefs.getString("leaderboard_data", "");
        if (raw.isEmpty()) return items;

        String[] lines = raw.split(";;");
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 4) {
                try {
                    int sc = Integer.parseInt(parts[0]);
                    int acc = Integer.parseInt(parts[1]);
                    items.add(new LeaderboardItem(sc, acc, parts[2], parts[3]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return items;
    }

    private void saveLeaderboardEntry(int sc, int acc, String diff) {
        if (sc <= 0) return;
        List<LeaderboardItem> list = getLeaderboard();
        String dateStr = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date());
        list.add(new LeaderboardItem(sc, acc, diff, dateStr));

        // Sort descending by score, then accuracy
        Collections.sort(list, (a, b) -> {
            if (b.score != a.score) return Integer.compare(b.score, a.score);
            return Integer.compare(b.accuracy, a.accuracy);
        });

        // Retain top 8
        if (list.size() > 8) {
            list = list.subList(0, 8);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            LeaderboardItem item = list.get(i);
            if (i > 0) sb.append(";;");
            sb.append(item.score).append("|").append(item.accuracy).append("|").append(item.difficulty).append("|").append(item.date);
        }
        prefs.edit().putString("leaderboard_data", sb.toString()).apply();
    }

    private void loadSettings() {
        difficulty = prefs.getString("difficulty", "Medium");
        add = prefs.getBoolean("add", true);
        sub = prefs.getBoolean("sub", false);
        mul = prefs.getBoolean("mul", false);
        div = prefs.getBoolean("div", false);
        timerSeconds = prefs.getInt("timer", 45);
        soundEnabled = prefs.getBoolean("sound_enabled", true);
        problemType = prefs.getString("problem_type", "Word Problems");
    }

    private void loadPendingSettings() {
        pendingDifficulty = difficulty;
        pendingAdd = add;
        pendingSub = sub;
        pendingMul = mul;
        pendingDiv = div;
        pendingTimerSeconds = timerSeconds;
        pendingSoundEnabled = soundEnabled;
        pendingProblemType = problemType;
    }

    private void discardPendingSettings() {
        loadSettings();
        loadPendingSettings();
    }

    private void saveSettings() {
        if (!pendingAdd && !pendingSub && !pendingMul && !pendingDiv) {
            pendingAdd = true; // Keep the app valid even when user unticks every operation.
        }
        difficulty = pendingDifficulty;
        add = pendingAdd;
        sub = pendingSub;
        mul = pendingMul;
        div = pendingDiv;
        timerSeconds = pendingTimerSeconds;
        soundEnabled = pendingSoundEnabled;
        problemType = pendingProblemType;
        prefs.edit().putString("difficulty", difficulty)
                .putBoolean("add", add).putBoolean("sub", sub)
                .putBoolean("mul", mul).putBoolean("div", div)
                .putInt("timer", timerSeconds)
                .putBoolean("sound_enabled", soundEnabled)
                .putString("problem_type", problemType).apply();
    }

    private String bestKey() {
        return "best_" + difficulty.toLowerCase(Locale.US);
    }

    private void startGame() {
        if (timer != null) timer.cancel();
        loadSettings();
        bestScore = prefs.getInt(bestKey(), 0);
        screen = 1;
        score = 0;
        correct = 0;
        attempted = 0;
        questionNumber = 0;
        locked = false;
        sessionFinished = false;
        feedback = "Think about it!";
        feedbackColor = MUTED;
        particles.clear();
        nextQuestion();
        view.invalidate();
    }

    // Real-Life Word Problem Generator
    private String generateWordProblem(String op, int a, int b) {
        int choice = rng.nextInt(3);
        if (op.equals("−")) {
            if (choice == 0) return "You have ₱" + a + " and buy food\nworth ₱" + b + ". How much is left?";
            if (choice == 1) return "Maria saved ₱" + a + " and spent\n₱" + b + " on supplies. Money left?";
            return "A store had " + a + " notebooks and\nsold " + b + ". How many left?";
        } else if (op.equals("+")) {
            if (choice == 0) return "Juan saved ₱" + a + " on Monday\nand ₱" + b + " on Tuesday. Total?";
            if (choice == 1) return "You bought snacks for ₱" + a + "\nand drink for ₱" + b + ". Total cost?";
            return "The library received " + a + " books\nand " + b + " more. Total books?";
        } else if (op.equals("×")) {
            if (choice == 0) return "Each notebook costs ₱" + a + ".\nHow much will " + b + " notebooks cost?";
            if (choice == 1) return "There are " + b + " rooms with " + a + "\nstudents each. Total students?";
            return "You buy " + b + " boxes of crayons.\nEach box has " + a + ". Total crayons?";
        } else { // Division
            if (choice == 0) return "₱" + a + " is shared equally among\n" + b + " students. How much each?";
            if (choice == 1) return "You have " + a + " candies to share\nequally into " + b + " bags. Per bag?";
            return "" + a + " students form " + b + " equal\nteams. How many per team?";
        }
    }

    private void nextQuestion() {
        ArrayList<String> ops = new ArrayList<>();
        if (add) ops.add("+");
        if (sub) ops.add("−");
        if (mul) ops.add("×");
        if (div) ops.add("÷");
        if (ops.isEmpty()) { add = true; ops.add("+"); }
        opSymbol = ops.get(rng.nextInt(ops.size()));

        if (difficulty.equals("Easy")) {
            if (opSymbol.equals("×")) {
                a = rng.nextInt(12) + 1; b = rng.nextInt(12) + 1; ans = a * b;
            } else if (opSymbol.equals("÷")) {
                int divisor = rng.nextInt(9) + 2;
                int quotient = rng.nextInt(12) + 1;
                b = divisor; ans = quotient; a = divisor * quotient;
            } else {
                int max = 99;
                a = rng.nextInt(max - 9) + 10; b = rng.nextInt(max - 9) + 10;
                if (opSymbol.equals("−") && b > a) { int t = a; a = b; b = t; }
                ans = opSymbol.equals("+") ? a + b : a - b;
            }
        } else if (difficulty.equals("Medium")) {
            if (opSymbol.equals("×")) {
                a = rng.nextInt(40) + 10; b = rng.nextInt(20) + 2; ans = a * b;
            } else if (opSymbol.equals("÷")) {
                int divisor = rng.nextInt(18) + 2;
                int quotient = rng.nextInt(40) + 5;
                b = divisor; ans = quotient; a = divisor * quotient;
            } else {
                int max = 199;
                a = rng.nextInt(max - 19) + 20; b = rng.nextInt(max - 19) + 20;
                if (opSymbol.equals("−") && b > a) { int t = a; a = b; b = t; }
                ans = opSymbol.equals("+") ? a + b : a - b;
            }
        } else {
            if (opSymbol.equals("×")) {
                a = rng.nextInt(90) + 10; b = rng.nextInt(50) + 2; ans = a * b;
            } else if (opSymbol.equals("÷")) {
                int divisor = rng.nextInt(48) + 2;
                int quotient = rng.nextInt(80) + 10;
                b = divisor; ans = quotient; a = divisor * quotient;
            } else {
                int max = 999;
                a = rng.nextInt(max - 99) + 100; b = rng.nextInt(max - 99) + 100;
                if (opSymbol.equals("−") && b > a) { int t = a; a = b; b = t; }
                ans = opSymbol.equals("+") ? a + b : a - b;
            }
        }

        boolean isWord = problemType.equals("Word Problems") || (problemType.equals("Mixed") && rng.nextBoolean());
        if (isWord) {
            questionText = generateWordProblem(opSymbol, a, b);
        } else {
            questionText = a + "  " + opSymbol + "  " + b + "  =";
        }

        questionNumber++;
        feedback = "Think about it!";
        feedbackColor = MUTED;
        locked = false;
        answer.setText("");
        showAnswer();
        restartTimer();
        view.invalidate();
    }

    private void restartTimer() {
        if (timer != null) timer.cancel();
        timeLeftMs = timerSeconds * 1000L;
        timer = new CountDownTimer(timeLeftMs, 100) {
            @Override public void onTick(long ms) { timeLeftMs = ms; view.invalidate(); }
            @Override public void onFinish() { timeLeftMs = 0; onTimeUp(); }
        }.start();
    }

    private void onTimeUp() {
        if (screen != 1 || sessionFinished) return;
        sessionFinished = true;
        locked = true;
        feedback = "Time is Over";
        feedbackColor = RED;
        hideAnswer();
        playTimeUpSound();

        // Update Study Streak & Leaderboard
        updateStreakOnPractice();
        saveLeaderboardEntry(score, getAccuracy(), difficulty);

        int currentBest = prefs.getInt(bestKey(), 0);
        if (score > currentBest) currentBest = score;
        prefs.edit().putInt(bestKey(), currentBest).apply();
        bestScore = currentBest;
        saveHistory();

        transitionTo(4);
    }

    private void checkAnswer() {
        if (screen != 1 || locked || sessionFinished) return;
        String s = answer.getText().toString().trim();
        if (s.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Answer needed")
                    .setMessage("Type your answer!")
                    .setPositiveButton("Ok", null)
                    .show();
            return;
        }
        attempted++;
        try {
            int entered = Integer.parseInt(s);
            if (entered == ans) {
                score++;
                correct++;
                feedback = "You're right!\nNext one.";
                feedbackColor = GREEN;
                locked = true;
                if (timer != null) timer.cancel();
                playCorrectSound();
                spawnCelebrationParticles();
                view.invalidate();
                view.postDelayed(() -> {
                    if (screen == 1 && !sessionFinished) nextQuestion();
                }, 780);
            } else {
                feedback = "Oops, try\nagain!";
                feedbackColor = RED;
                answer.selectAll();
                playWrongSound();
                view.invalidate();
            }
        } catch (NumberFormatException ignored) {
            new AlertDialog.Builder(this)
                    .setTitle("Invalid answer")
                    .setMessage("Please enter a whole number.")
                    .setPositiveButton("Ok", null)
                    .show();
        }
    }

    private void spawnCelebrationParticles() {
        particles.clear();
        int[] colors = new int[]{ROSE, PINK, LAVENDER, GOLD, GREEN};
        for (int i = 0; i < 24; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 14f;
            float vy = -rng.nextFloat() * 12f - 4f;
            float sz = rng.nextFloat() * 6f + 4f;
            int c = colors[rng.nextInt(colors.length)];
            particles.add(new Particle(180f, 180f, vx, vy, sz, c));
        }
    }

    private void updateAndDrawParticles(Canvas c, float scale, float ox, float oy) {
        if (particles.isEmpty()) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle pt = it.next();
            pt.x += pt.vx;
            pt.y += pt.vy;
            pt.vy += 0.45f; // Gravity
            pt.alpha -= 0.03f;
            if (pt.alpha <= 0) {
                it.remove();
                continue;
            }
            p.setColor(pt.color);
            p.setAlpha(Math.max(0, Math.min(255, (int)(pt.alpha * 255))));
            float cx = ox + pt.x * scale;
            float cy = oy + pt.y * scale;
            c.drawCircle(cx, cy, pt.size * scale, p);
        }
        if (!particles.isEmpty()) view.postInvalidateOnAnimation();
    }

    private void saveHistory() {
        if (attempted == 0) return;
        String old = prefs.getString("history", "");
        String stamp = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(new Date());
        String line = stamp + "|" + score + " correct / " + attempted + " attempted (" + getAccuracy() + "%)|" + difficulty;
        if (old.isEmpty()) old = line; else old = line + ";;" + old;
        String[] arr = old.split(";;");
        if (arr.length > 12) old = String.join(";;", Arrays.copyOf(arr, 12));
        prefs.edit().putString("history", old).apply();
    }

    private void backToMenu() {
        if (timer != null) timer.cancel();
        hideAnswer();
        screen = 0;
        sessionFinished = false;
        view.invalidate();
    }

    private void openSettings() {
        if (timer != null) timer.cancel();
        loadSettings();
        loadPendingSettings();
        screen = 2;
        hideAnswer();
        view.invalidate();
    }

    private void openHistory() {
        if (timer != null) timer.cancel();
        screen = 3;
        hideAnswer();
        view.invalidate();
    }

    private void openLeaderboard() {
        if (timer != null) timer.cancel();
        screen = 5;
        hideAnswer();
        view.invalidate();
    }

    private void transitionTo(int target) {
        previousScreen = screen;
        screen = target;
        hideAnswer();
        view.startSlide(previousScreen, target);
    }

    private void tryAgain() {
        transitionTo(1);
        view.postDelayed(this::startGame, 40);
    }

    @Override public void onBackPressed() {
        if (screen == 2) {
            discardPendingSettings();
            backToMenu();
        } else if (screen == 4) {
            backToMenu();
        } else if (screen != 0) {
            backToMenu();
        } else super.onBackPressed();
    }

    class SmartView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float scale = 1f, ox = 0f, oy = 0f;
        private float pressX = -1, pressY = -1;
        private String pressedKey = "";
        private float slide = 0f;
        private ValueAnimator slideAnimator;

        SmartView(Context c) { super(c); setFocusable(true); }
        float scale() { return scale; }
        float offsetX() { return ox; }
        float offsetY() { return oy; }
        float sx(float x) { return ox + x * scale + slide; }
        float sy(float y) { return oy + y * scale; }

        void text(Canvas c, String t, float x, float y, float size, int color,
                  Paint.Align align, boolean bold, Typeface typeface) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size * scale);
            p.setTextAlign(align);
            p.setTypeface(Typeface.create(typeface, bold ? Typeface.BOLD : Typeface.NORMAL));
            String[] rows = t.split("\\n", -1);
            float line = size * 1.16f * scale;
            for (int i = 0; i < rows.length; i++) c.drawText(rows[i], sx(x), sy(y + i * (line/scale)), p);
        }

        void serif(Canvas c, String t, float x, float y, float size, int color, Paint.Align a, boolean bold) {
            text(c, t, x, y, size, color, a, bold, Typeface.SERIF);
        }
        void sans(Canvas c, String t, float x, float y, float size, int color, Paint.Align a, boolean bold) {
            text(c, t, x, y, size, color, a, bold, Typeface.SANS_SERIF);
        }

        void rounded(Canvas c, float l, float t, float r, float b, float rad, int fill, int stroke, int strokeColor) {
            RectF rr = new RectF(sx(l), sy(t), sx(r), sy(b));
            p.setStyle(Paint.Style.FILL); p.setColor(fill);
            c.drawRoundRect(rr, rad*scale, rad*scale, p);
            if (stroke > 0) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(stroke*scale); p.setColor(strokeColor);
                c.drawRoundRect(rr, rad*scale, rad*scale, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            scale = Math.min(getWidth()/DESIGN_W, getHeight()/DESIGN_H);
            ox = (getWidth() - DESIGN_W*scale)/2f;
            oy = (getHeight() - DESIGN_H*scale)/2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(BG);
            c.drawRect(0,0,getWidth(),getHeight(),p);

            if (slide != 0f && previousScreen != screen) {
                float activeSlide = slide;
                float previousShift = activeSlide > 0 ? activeSlide - DESIGN_W * scale : activeSlide + DESIGN_W * scale;
                slide = previousShift;
                drawScreen(c, previousScreen);
                slide = activeSlide;
                drawScreen(c, screen);
            } else {
                drawScreen(c, screen);
            }
            positionAnswer();
        }

        private void drawScreen(Canvas c, int which) {
            drawBackground(c);
            if (which == 0) drawMenu(c);
            else if (which == 1) drawQuiz(c);
            else if (which == 2) drawSettings(c);
            else if (which == 3) drawHistory(c);
            else if (which == 4) drawTimeOver(c);
            else drawLeaderboard(c);
        }

        void drawBackground(Canvas c) {
            p.setStyle(Paint.Style.FILL); p.setColor(BG); c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setColor(BG2);
            blob(c, -20, 20, 130, 150, true);
            p.setColor(BG3);
            blob(c, 250, 555, 130, 170, false);
            drawSparkle(c, 83, 92, 8, Color.argb(155,201,186,246));
            drawSparkle(c, 286, 670, 6, Color.argb(130,255,188,212));
        }

        void blob(Canvas c, float x, float y, float w, float h, boolean alpha) {
            p.setColor(Color.argb(alpha ? 105 : 75, 231, 123, 167));
            RectF rr = new RectF(sx(x),sy(y),sx(x+w),sy(y+h));
            c.drawOval(rr,p);
        }

        void drawSparkle(Canvas c, float x, float y, float r, int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(sx(x), sy(y-r)); path.lineTo(sx(x+r*0.25f), sy(y-r*0.25f));
            path.lineTo(sx(x+r), sy(y)); path.lineTo(sx(x+r*0.25f), sy(y+r*0.25f));
            path.lineTo(sx(x), sy(y+r)); path.lineTo(sx(x-r*0.25f), sy(y+r*0.25f));
            path.lineTo(sx(x-r), sy(y)); path.lineTo(sx(x-r*0.25f), sy(y-r*0.25f)); path.close();
            c.drawPath(path,p);
        }

        void drawHeart(Canvas c, float x, float y, float s, int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(sx(x), sy(y+23*s));
            path.cubicTo(sx(x-34*s), sy(y+2*s), sx(x-27*s), sy(y-19*s), sx(x-12*s), sy(y-12*s));
            path.cubicTo(sx(x-4*s), sy(y-25*s), sx(x+14*s), sy(y-25*s), sx(x+14*s), sy(y-10*s));
            path.cubicTo(sx(x+33*s), sy(y-21*s), sx(x+39*s), sy(y+3*s), sx(x), sy(y+23*s));
            c.drawPath(path,p);
        }

        void title(Canvas c, String a, String b, float y) {
            serif(c, a, 180, y, 40, WHITE, Paint.Align.CENTER, true);
            serif(c, b, 180, y+40, 40, ROSE, Paint.Align.CENTER, true);
        }

        String keyForButton(float y, String label) { return label + "@" + (int)y; }

        void menuButton(Canvas c, float y, String label, int color) {
            boolean down = pressedKey.equals(keyForButton(y,label));
            int main = down ? darken(color, 0.72f) : color;
            rounded(c, 59, y+6, 301, y+52, 24, Color.rgb(151,70,111), 0, 0);
            rounded(c, 55, y, 297, y+46, 24, main, 2, down ? Color.rgb(167,64,111) : Color.rgb(255,205,225));
            serif(c, label, 176, y+30, 18, textColorForButton(main), Paint.Align.CENTER, true);
        }

        int textColorForButton(int c) { return (c == PINK_DARK || c == darken(PINK_DARK,0.72f)) ? WHITE : BG; }
        int darken(int c, float factor) {
            return Color.rgb(Math.max(0,Math.round(Color.red(c)*factor)), Math.max(0,Math.round(Color.green(c)*factor)), Math.max(0,Math.round(Color.blue(c)*factor)));
        }

        void drawMenu(Canvas c) {
            title(c, "Math", "Smart", 145);
            serif(c, "Arithmetic Exercises", 180, 235, 15, MUTED, Paint.Align.CENTER, false);
            sans(c, "Enhancing mathematical skills", 180, 255, 9, Color.argb(210,234,219,239), Paint.Align.CENTER, false);

            // STUDY STREAK BADGE DISPLAY
            rounded(c, 85, 275, 275, 307, 16, Color.argb(90, 247, 190, 103), 1, Color.rgb(247, 190, 103));
            serif(c, "🔥 " + currentStreak + "-Day Math Streak", 180, 296, 12, GOLD, Paint.Align.CENTER, true);

            menuButton(c, 325, "Start", PINK_DARK);
            menuButton(c, 385, "Leaderboard", PINK_DARK);
            menuButton(c, 445, "Settings", PINK_DARK);
            menuButton(c, 505, "History", PINK_DARK);

            drawHeart(c, 278, 155, 0.62f, Color.argb(150,255,188,212));
            drawSparkle(c, 85, 153, 9, Color.argb(150,201,186,246));
            sans(c, "Bestlink College of the Philippines", 180, 716, 8, Color.argb(190,234,219,239), Paint.Align.CENTER, false);
        }

        void backButton(Canvas c) {
            boolean down = pressedKey.equals("back");
            rounded(c, 15, 16, 51, 52, 18, down ? Color.rgb(235,214,228) : CREAM, 0, 0);
            p.setColor(PINK_DARK); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.2f*scale);
            path.reset(); path.moveTo(sx(36),sy(25)); path.lineTo(sx(27),sy(34)); path.lineTo(sx(36),sy(43)); c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
        }

        void keyButton(Canvas c, float x, float y, float w, float h, String keyLabel) {
            boolean down = pressedKey.equals("num_" + keyLabel);
            int bgCol = down ? Color.argb(160, 231, 123, 167) : Color.argb(70, 255, 255, 255);
            rounded(c, x, y, x + w, y + h, 12, bgCol, 1, Color.argb(100, 255, 205, 225));
            serif(c, keyLabel, x + w / 2f, y + h / 2f + 6, 18, WHITE, Paint.Align.CENTER, true);
        }

        void drawQuiz(Canvas c) {
            backButton(c);

            sans(c, "Score: " + score + "   |   Accuracy: " + getAccuracy() + "%", 180, 36, 12, WHITE, Paint.Align.CENTER, true);
            serif(c, feedback, 180, 68, 17, feedbackColor, Paint.Align.CENTER, true);

            float totalTimerMs = timerSeconds * 1000f;
            float progress = Math.max(0f, Math.min(1f, timeLeftMs / totalTimerMs));
            rounded(c, 40, 92, 320, 101, 5, Color.argb(60, 255, 255, 255), 0, 0);
            int barColor = (timeLeftMs <= 5000) ? RED : GREEN;
            if (progress > 0) {
                rounded(c, 40, 92, 40 + (280 * progress), 101, 5, barColor, 0, 0);
            }

            String secs = String.format(Locale.US, "%02d", Math.max(0, (int)((timeLeftMs + 999)/1000)));
            sans(c, secs + " sec left", 180, 116, 11, WHITE, Paint.Align.CENTER, false);
            sans(c, "Question " + questionNumber, 180, 132, 9, Color.argb(180,234,219,239), Paint.Align.CENTER, false);

            if (questionText.contains("\n")) {
                serif(c, questionText, 180, 155, 12, WHITE, Paint.Align.CENTER, true);
            } else {
                serif(c, questionText, 180, 168, 26, WHITE, Paint.Align.CENTER, true);
            }

            // Answer Box
            rounded(c, 79, 202, 281, 246, 7, Color.argb(96,255,255,255), 1, Color.argb(100,255,255,255));
            rounded(c, 80, 203, 280, 245, 6, Color.argb(30,20,10,35), 0, 0);

            // Custom Keypad Grid
            keyButton(c, 60,  258, 66, 40, "1");
            keyButton(c, 147, 258, 66, 40, "2");
            keyButton(c, 234, 258, 66, 40, "3");

            keyButton(c, 60,  306, 66, 40, "4");
            keyButton(c, 147, 306, 66, 40, "5");
            keyButton(c, 234, 306, 66, 40, "6");

            keyButton(c, 60,  354, 66, 40, "7");
            keyButton(c, 147, 354, 66, 40, "8");
            keyButton(c, 234, 354, 66, 40, "9");

            keyButton(c, 60,  402, 66, 40, "−");
            keyButton(c, 147, 402, 66, 40, "0");
            keyButton(c, 234, 402, 66, 40, "⌫");

            menuButton(c, 456, "Check", PINK_DARK);

            updateAndDrawParticles(c, scale, ox, oy);
        }

        void drawTimeOver(Canvas c) {
            drawHeart(c, 180, 85, 0.82f, Color.argb(115,255,188,212));
            serif(c, "Time is Over", 180, 155, 30, WHITE, Paint.Align.CENTER, true);

            // DISPLAY STREAK BADGE ON TIME OVER
            rounded(c, 90, 185, 270, 215, 14, Color.argb(90, 247, 190, 103), 1, Color.rgb(247, 190, 103));
            serif(c, "🔥 " + currentStreak + "-Day Math Streak", 180, 205, 11, GOLD, Paint.Align.CENTER, true);

            serif(c, "Your score: " + score, 180, 240, 20, ROSE, Paint.Align.CENTER, true);
            sans(c, "Accuracy: " + getAccuracy() + "%", 180, 266, 14, GOLD, Paint.Align.CENTER, true);
            sans(c, "Best: " + bestScore, 180, 288, 13, LAVENDER, Paint.Align.CENTER, true);
            sans(c, correct + " correct out of " + attempted + " attempted", 180, 318, 11, MUTED, Paint.Align.CENTER, false);

            menuButton(c, 370, "Try again", PINK_DARK);
            menuButton(c, 444, "Main menu", PINK_DARK);
        }

        void drawLeaderboard(Canvas c) {
            backButton(c);
            sans(c, "Leaderboard", 180, 39, 17, WHITE, Paint.Align.CENTER, true);
            sans(c, "Top High Score Rankings", 180, 61, 9, MUTED, Paint.Align.CENTER, false);

            List<LeaderboardItem> items = getLeaderboard();
            if (items.isEmpty()) {
                drawHeart(c, 180, 170, 1.0f, Color.argb(130,255,188,212));
                sans(c, "No high scores yet.", 180, 208, 15, WHITE, Paint.Align.CENTER, true);
                sans(c, "Complete a session to get on the Leaderboard!", 180, 230, 10, MUTED, Paint.Align.CENTER, false);
            } else {
                float y = 91;
                String[] medals = new String[]{"🏆 #1", "🥈 #2", "🥉 #3", "#4", "#5", "#6"};
                for (int i = 0; i < items.size(); i++) {
                    LeaderboardItem item = items.get(i);
                    rounded(c, 24, y, 336, y + 60, 14, Color.argb(52, 255, 250, 252), 1, Color.argb(70, 255, 205, 225));

                    String rankStr = (i < medals.length) ? medals[i] : ("#" + (i + 1));
                    int rankColor = (i == 0) ? GOLD : (i == 1 ? CREAM : (i == 2 ? ROSE : WHITE));

                    serif(c, rankStr, 40, y + 36, 14, rankColor, Paint.Align.LEFT, true);
                    serif(c, "Score: " + item.score + " (" + item.accuracy + "%)", 110, y + 26, 13, WHITE, Paint.Align.LEFT, true);
                    sans(c, item.difficulty + " • " + item.date, 110, y + 46, 9, LAVENDER, Paint.Align.LEFT, false);

                    y += 68;
                    if (i >= 5) break;
                }
            }
            menuButton(c, 647, "Clear Leaderboard", PINK_DARK);
        }

        void checkbox(Canvas c, float x, float y, boolean checked, boolean down) {
            rounded(c, x, y, x+18, y+18, 4, down ? Color.argb(80,255,255,255) : Color.TRANSPARENT, 2, PINK);
            if (checked) {
                p.setColor(PINK); p.setStyle(Paint.Style.FILL); c.drawRect(sx(x+3),sy(y+3),sx(x+15),sy(y+15),p);
                p.setColor(WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f*scale);
                path.reset(); path.moveTo(sx(x+5),sy(y+9)); path.lineTo(sx(x+8),sy(y+12)); path.lineTo(sx(x+13),sy(y+6)); c.drawPath(path,p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void settingRow(Canvas c, String label, float y, boolean checked, boolean down) {
            checkbox(c, 34, y, checked, down);
            sans(c, label, 62, y+13, 12, WHITE, Paint.Align.LEFT, false);
        }

        boolean settingDown(float y) { return pressedKey.equals("setting@" + (int)y); }

        void drawSettings(Canvas c) {
            backButton(c);
            sans(c, "Settings", 180, 39, 17, WHITE, Paint.Align.CENTER, true);

            sans(c, "Choose difficulty", 34, 78, 11, WHITE, Paint.Align.LEFT, true);
            settingRow(c, "Easy", 93, pendingDifficulty.equals("Easy"), settingDown(93));
            settingRow(c, "Medium", 123, pendingDifficulty.equals("Medium"), settingDown(123));
            settingRow(c, "Hard", 153, pendingDifficulty.equals("Hard"), settingDown(153));

            sans(c, "Select operations", 34, 192, 11, WHITE, Paint.Align.LEFT, true);
            settingRow(c, "Addition", 207, pendingAdd, settingDown(207));
            settingRow(c, "Subtraction", 235, pendingSub, settingDown(235));
            settingRow(c, "Multiplication", 263, pendingMul, settingDown(263));
            settingRow(c, "Division", 291, pendingDiv, settingDown(291));

            sans(c, "Set a timer", 34, 330, 11, WHITE, Paint.Align.LEFT, true);
            settingRow(c, "15 seconds", 345, pendingTimerSeconds == 15, settingDown(345));
            settingRow(c, "30 seconds", 373, pendingTimerSeconds == 30, settingDown(373));
            settingRow(c, "45 seconds", 401, pendingTimerSeconds == 45, settingDown(401));

            sans(c, "Question Format", 34, 440, 11, WHITE, Paint.Align.LEFT, true);
            settingRow(c, "Word Problems", 455, pendingProblemType.equals("Word Problems"), settingDown(455));
            settingRow(c, "Standard Equations", 483, pendingProblemType.equals("Equations"), settingDown(483));
            settingRow(c, "Mixed", 511, pendingProblemType.equals("Mixed"), settingDown(511));

            sans(c, "Audio Settings", 34, 548, 11, WHITE, Paint.Align.LEFT, true);
            settingRow(c, "Sound Effects (SFX)", 563, pendingSoundEnabled, settingDown(563));

            menuButton(c, 647, "Save", PINK_DARK);
        }

        void drawHistory(Canvas c) {
            backButton(c);
            sans(c, "History", 180, 39, 17, WHITE, Paint.Align.CENTER, true);
            sans(c, "Recent practice sessions", 180, 61, 9, MUTED, Paint.Align.CENTER, false);
            String h = prefs.getString("history", "");
            if (h.isEmpty()) {
                drawHeart(c, 180, 170, 1.0f, Color.argb(130,255,188,212));
                sans(c, "No sessions yet.", 180, 208, 15, WHITE, Paint.Align.CENTER, true);
                sans(c, "Finish a MathSmart session to see it here.", 180, 230, 10, MUTED, Paint.Align.CENTER, false);
            } else {
                String[] lines = h.split(";;");
                float y = 91; int shown = 0;
                for (String line : lines) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 3) continue;
                    rounded(c, 24, y, 336, y+64, 14, Color.argb(52,255,250,252), 1, Color.argb(70,255,205,225));
                    sans(c, parts[0], 38, y+20, 9, WHITE, Paint.Align.LEFT, false);
                    serif(c, parts[1], 38, y+44, 12, ROSE, Paint.Align.LEFT, true);
                    sans(c, parts[2], 318, y+44, 10, LAVENDER, Paint.Align.RIGHT, true);
                    y += 74;
                    if (++shown >= 7) break;
                }
            }
            menuButton(c, 647, "Clear History", PINK_DARK);
        }

        void startSlide(int from, int to) {
            if (slideAnimator != null) slideAnimator.cancel();
            previousScreen = from;
            screen = to;
            final float start = (to > from) ? DESIGN_W * scale : -DESIGN_W * scale;
            slide = start;
            invalidate();
            slideAnimator = ValueAnimator.ofFloat(start, 0f);
            slideAnimator.setDuration(260);
            slideAnimator.setInterpolator(new DecelerateInterpolator());
            slideAnimator.addUpdateListener(animation -> {
                slide = (float) animation.getAnimatedValue();
                invalidate();
            });
            slideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    slide = 0f; invalidate(); positionAnswer();
                }
            });
            slideAnimator.start();
        }

        private boolean hit(float x,float y,float l,float t,float r,float b) {
            return x>=l && x<=r && y>=t && y<=b;
        }

        private String buttonAt(float x, float y) {
            if (screen == 0) {
                if (hit(x,y,55,325,297,371)) return keyForButton(325,"Start");
                if (hit(x,y,55,385,297,431)) return keyForButton(385,"Leaderboard");
                if (hit(x,y,55,445,297,491)) return keyForButton(445,"Settings");
                if (hit(x,y,55,505,297,551)) return keyForButton(505,"History");
            } else if (screen == 1) {
                if (hit(x,y,15,16,51,52)) return "back";

                if (hit(x,y,60,258,126,298)) return "num_1";
                if (hit(x,y,147,258,213,298)) return "num_2";
                if (hit(x,y,234,258,300,298)) return "num_3";

                if (hit(x,y,60,306,126,346)) return "num_4";
                if (hit(x,y,147,306,213,346)) return "num_5";
                if (hit(x,y,234,306,300,346)) return "num_6";

                if (hit(x,y,60,354,126,394)) return "num_7";
                if (hit(x,y,147,354,213,394)) return "num_8";
                if (hit(x,y,234,354,300,394)) return "num_9";

                if (hit(x,y,60,402,126,442)) return "num_−";
                if (hit(x,y,147,402,213,442)) return "num_0";
                if (hit(x,y,234,402,300,442)) return "num_⌫";

                if (hit(x,y,55,456,297,510)) return keyForButton(456,"Check");
            } else if (screen == 2) {
                if (hit(x,y,15,16,51,52)) return "back";
                if (hit(x,y,25,84,220,111)) return "setting@93";
                if (hit(x,y,25,114,220,141)) return "setting@123";
                if (hit(x,y,25,144,220,171)) return "setting@153";

                if (hit(x,y,25,198,260,223)) return "setting@207";
                if (hit(x,y,25,226,260,251)) return "setting@235";
                if (hit(x,y,25,254,260,279)) return "setting@263";
                if (hit(x,y,25,282,260,307)) return "setting@291";

                if (hit(x,y,25,336,260,361)) return "setting@345";
                if (hit(x,y,25,364,260,389)) return "setting@373";
                if (hit(x,y,25,392,260,417)) return "setting@401";

                if (hit(x,y,25,446,260,471)) return "setting@455";
                if (hit(x,y,25,474,260,499)) return "setting@483";
                if (hit(x,y,25,502,260,527)) return "setting@511";

                if (hit(x,y,25,554,260,579)) return "setting@563";
                if (hit(x,y,55,647,297,701)) return keyForButton(647,"Save");
            } else if (screen == 3) {
                if (hit(x,y,15,16,51,52)) return "back";
                if (hit(x,y,55,647,297,701)) return keyForButton(647,"Clear History");
            } else if (screen == 4) {
                if (hit(x,y,55,370,297,424)) return keyForButton(370,"Try again");
                if (hit(x,y,55,444,297,498)) return keyForButton(444,"Main menu");
            } else if (screen == 5) {
                if (hit(x,y,15,16,51,52)) return "back";
                if (hit(x,y,55,647,297,701)) return keyForButton(647,"Clear Leaderboard");
            }
            return "";
        }

        private void handleKeypadInput(String key) {
            if (locked || sessionFinished) return;
            playClickSound();
            String current = answer.getText().toString();
            if (key.equals("⌫")) {
                if (!current.isEmpty()) {
                    answer.setText(current.substring(0, current.length() - 1));
                    answer.setSelection(answer.getText().length());
                }
            } else if (key.equals("−")) {
                if (current.startsWith("-")) {
                    answer.setText(current.substring(1));
                } else {
                    answer.setText("-" + current);
                }
                answer.setSelection(answer.getText().length());
            } else { // Digits 0-9
                if (current.length() < 7) {
                    answer.setText(current + key);
                    answer.setSelection(answer.getText().length());
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = (e.getX() - ox - slide) / scale;
            float y = (e.getY() - oy) / scale;

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                pressedKey = buttonAt(x,y);
                pressX = x; pressY = y;
                invalidate();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_CANCEL) {
                pressedKey = ""; invalidate(); return true;
            }
            if (e.getAction() != MotionEvent.ACTION_UP) return true;

            String pressed = pressedKey;
            pressedKey = "";
            boolean same = pressed.length() > 0 && pressed.equals(buttonAt(x,y));
            invalidate();
            if (!same) return true;

            if (screen == 0) {
                if (pressed.equals(keyForButton(325,"Start"))) { playClickSound(); startGame(); }
                else if (pressed.equals(keyForButton(385,"Leaderboard"))) { playClickSound(); openLeaderboard(); }
                else if (pressed.equals(keyForButton(445,"Settings"))) { playClickSound(); openSettings(); }
                else if (pressed.equals(keyForButton(505,"History"))) { playClickSound(); openHistory(); }
            } else if (screen == 1) {
                if (pressed.equals("back")) { playClickSound(); backToMenu(); }
                else if (pressed.startsWith("num_")) {
                    handleKeypadInput(pressed.substring(4));
                }
                else if (hit(x,y,79,202,281,246)) {
                    if (!locked && !sessionFinished) {
                        answer.requestFocus();
                        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(answer, InputMethodManager.SHOW_IMPLICIT);
                    }
                } else if (pressed.equals(keyForButton(456,"Check"))) checkAnswer();
            } else if (screen == 2) {
                if (pressed.equals("back")) {
                    playClickSound();
                    discardPendingSettings();
                    backToMenu();
                } else if (pressed.equals("setting@93")) { playClickSound(); pendingDifficulty = "Easy"; }
                else if (pressed.equals("setting@123")) { playClickSound(); pendingDifficulty = "Medium"; }
                else if (pressed.equals("setting@153")) { playClickSound(); pendingDifficulty = "Hard"; }
                else if (pressed.equals("setting@207")) { playClickSound(); pendingAdd = !pendingAdd; }
                else if (pressed.equals("setting@235")) { playClickSound(); pendingSub = !pendingSub; }
                else if (pressed.equals("setting@263")) { playClickSound(); pendingMul = !pendingMul; }
                else if (pressed.equals("setting@291")) { playClickSound(); pendingDiv = !pendingDiv; }
                else if (pressed.equals("setting@345")) { playClickSound(); pendingTimerSeconds = 15; }
                else if (pressed.equals("setting@373")) { playClickSound(); pendingTimerSeconds = 30; }
                else if (pressed.equals("setting@401")) { playClickSound(); pendingTimerSeconds = 45; }
                else if (pressed.equals("setting@455")) { playClickSound(); pendingProblemType = "Word Problems"; }
                else if (pressed.equals("setting@483")) { playClickSound(); pendingProblemType = "Equations"; }
                else if (pressed.equals("setting@511")) { playClickSound(); pendingProblemType = "Mixed"; }
                else if (pressed.equals("setting@563")) {
                    pendingSoundEnabled = !pendingSoundEnabled;
                    if (pendingSoundEnabled) playClickSound();
                }
                else if (pressed.equals(keyForButton(647,"Save"))) {
                    playClickSound();
                    saveSettings();
                    backToMenu();
                }
                invalidate();
            } else if (screen == 3) {
                if (pressed.equals("back")) { playClickSound(); backToMenu(); }
                else if (pressed.equals(keyForButton(647,"Clear History"))) {
                    playClickSound();
                    prefs.edit().remove("history").apply();
                    invalidate();
                }
            } else if (screen == 4) {
                if (pressed.equals(keyForButton(370,"Try again"))) { playClickSound(); tryAgain(); }
                else if (pressed.equals(keyForButton(444,"Main menu"))) { playClickSound(); backToMenu(); }
            } else if (screen == 5) {
                if (pressed.equals("back")) { playClickSound(); backToMenu(); }
                else if (pressed.equals(keyForButton(647,"Clear Leaderboard"))) {
                    playClickSound();
                    prefs.edit().remove("leaderboard_data").apply();
                    invalidate();
                }
            }
            return true;
        }
    }
}
