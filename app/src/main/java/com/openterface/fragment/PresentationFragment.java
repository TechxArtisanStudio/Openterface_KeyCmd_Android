package com.openterface.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.openterface.keymod.ConnectionManager;
import com.openterface.keymod.MainActivity;
import com.openterface.keymod.R;
import com.openterface.keymod.util.PopOutTouchPadDialog;

import java.util.concurrent.TimeUnit;

/**
 * Presentation Mode Fragment - Remote control for presentations.
 * Supports slide navigation (via arrow keys), timer, pace tracking, laser pointer, and black screen.
 */
public class PresentationFragment extends Fragment {

    private static final String TAG = "PresentationFragment";

    // Preference keys
    private static final String PREF_TIMER_DURATION = "presentation_timer_duration";
    private static final String PREF_TIMER_REMAINING = "presentation_timer_remaining";
    private static final String PREF_TIMER_RUNNING = "presentation_timer_running";
    private static final String PREF_BLACK_SCREEN = "presentation_black_screen";
    private static final String PREF_TIMER_MODE = "presentation_timer_mode";
    private static final String PREF_TIMER_COUNTDOWN_OVERTIME = "presentation_timer_countdown_overtime";
    private static final String PREF_TOOL_INDEX = "presentation_tool_index";

    private static final int DEFAULT_TIMER_DURATION = 25 * 60; // 25 minutes in seconds

    public static final int TIMER_MODE_COUNTDOWN = 0;
    public static final int TIMER_MODE_COUNTUP = 1;

    // HID key codes
    private static final int KEY_RIGHT_ARROW = 79;
    private static final int KEY_LEFT_ARROW = 80;
    private static final int KEY_B = 5; // For black screen toggle
    private static final int KEY_TAB = 43;
    private static final int KEY_ESCAPE = 41;
    private static final int KEY_P = 19;
    private static final int KEY_L = 15;
    private static final int KEY_RETURN = 40;
    private static final int KEY_HOME = 74;
    private static final int KEY_END = 77;
    private static final int KEY_F5 = 68;
    private static final int KEY_COMMAND = 227; // Left GUI/Cmd key

    // Presentation tools and their key mappings
    enum PresentationTool {
        KEYNOTE(R.string.presentation_tool_keynote,
                0x04 | 0x08, KEY_P,    // Opt+Cmd+P
                KEY_ESCAPE,
                R.string.presentation_shortcut_keynote),
        POWERPOINT(R.string.presentation_tool_powerpoint,
                0x02 | 0x08, KEY_RETURN, // Cmd+Shift+Enter (macOS)
                KEY_ESCAPE,
                R.string.presentation_shortcut_powerpoint),
        GOOGLE_SLIDES(R.string.presentation_tool_google_slides,
                0x08, KEY_RETURN,        // Cmd+Enter
                KEY_ESCAPE,
                R.string.presentation_shortcut_google_slides),
        WORD(R.string.presentation_tool_word,
                0, 0,                    // No play key
                KEY_ESCAPE,
                R.string.presentation_shortcut_word),
        ADOBE_READER(R.string.presentation_tool_adobe_reader,
                0x08, KEY_L,             // Cmd+L (toggle fullscreen)
                KEY_ESCAPE,
                R.string.presentation_shortcut_adobe_reader);

        @StringRes final int nameRes;
        final int playModifier;
        final int playKey;
        final int stopKey;
        @StringRes final int shortcutHintRes;

        PresentationTool(@StringRes int nameRes, int playModifier, int playKey, int stopKey,
                         @StringRes int shortcutHintRes) {
            this.nameRes = nameRes;
            this.playModifier = playModifier;
            this.playKey = playKey;
            this.stopKey = stopKey;
            this.shortcutHintRes = shortcutHintRes;
        }
    }

    // UI Views
    private TextView timerRemaining;
    private TextView timerHeaderLabel;
    private TextView timerStatusText;
    private FrameLayout timerCardContainer;
    private View timerProgressFill;
    private Button btnNext;
    private Button btnPrevious;
    private Button btnPlay;
    private Button btnBlackScreen;
    private Button btnLaser;
    private Button btnPointer;
    private TextView hintPlay;

    // Tool switcher views
    private RecyclerView toolCarousel;
    private LinearSnapHelper toolSnapHelper;
    private ToolCarouselAdapter toolAdapter;
    private boolean isProgrammaticScroll = false;
    private boolean carouselSidePaddingApplied;

    // State
    private int timerDuration = DEFAULT_TIMER_DURATION; // in seconds
    private int timerRemainingSec = timerDuration;
    private int timerMode = TIMER_MODE_COUNTDOWN; // 0=countdown, 1=countup
    private boolean countdownOvertime = false; // In countdown mode, true after reaching 00:00
    private boolean timerTargetAlerted = false; // Prevent repeated vibration every second
    private boolean timerRunning = false;
    private boolean blackScreenActive = false;
    private boolean appSwitcherActive = false; // Cmd held, app switcher is open
    private boolean playActive = false; // Presentation play toggled

    // Tool state
    private PresentationTool currentTool = PresentationTool.KEYNOTE;

    // Timer
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long timerStartTime = 0;

    // Vibrator
    private Vibrator vibrator;

    // Gesture detector for swipe support
    private GestureDetector gestureDetector;

    // Root view for gesture handling
    private View rootView;

    private SharedPreferences prefs;

    private PopOutTouchPadDialog popOutTouchPad;

    /**
     * Locks to normal and reverse portrait only (no landscape), matching {@link KeyboardMouseFragment}
     * touchpad portrait-pair behavior. {@link ActivityInfo#SCREEN_ORIENTATION_SENSOR_PORTRAIT} alone is
     * unreliable on some devices.
     */
    private OrientationEventListener portraitPairOrientationListener;
    private int lastPortraitPairLock = Integer.MIN_VALUE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_presentation, container, false);
        rootView = view;

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        timerHandler = new Handler(Looper.getMainLooper());

        // Load saved state
        loadState();

        // Initialize views
        initViews(view);
        setupListeners();
        setupGestureDetector(view);

        // Update UI
        updateTimerDisplay();

        // Resume timer if it was running
        if (timerRunning && timerRemainingSec > 0) {
            startTimer();
        }

        return view;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isAdded()) {
            return;
        }
        if (portraitPairOrientationListener != null
                && portraitPairOrientationListener.canDetectOrientation()) {
            syncPresentationPortraitLockFromDisplayRotation();
        }
    }

    @Override
    public void onDestroyView() {
        if (popOutTouchPad != null) {
            popOutTouchPad.dismissIfShowing();
        }
        super.onDestroyView();
    }

    private void initViews(View view) {
        timerRemaining = view.findViewById(R.id.timer_remaining);
        timerHeaderLabel = view.findViewById(R.id.timer_header_label);
        timerStatusText = view.findViewById(R.id.timer_status_text);
        timerCardContainer = view.findViewById(R.id.timer_card_container);
        timerProgressFill = view.findViewById(R.id.timer_progress_fill);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnPlay = view.findViewById(R.id.btn_play);
        btnBlackScreen = view.findViewById(R.id.btn_black_screen);
        btnLaser = view.findViewById(R.id.btn_laser);
        btnPointer = view.findViewById(R.id.btn_pointer);
        hintPlay = view.findViewById(R.id.hint_play);

        toolCarousel = view.findViewById(R.id.tool_carousel);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        toolCarousel.setLayoutManager(layoutManager);

        toolAdapter = new ToolCarouselAdapter(PresentationTool.values(), this::selectTool);
        toolCarousel.setAdapter(toolAdapter);

        // SnapHelper to center the selected item
        toolSnapHelper = new LinearSnapHelper();
        toolSnapHelper.attachToRecyclerView(toolCarousel);

        toolCarousel.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int w = right - left;
            if (w <= 0) return;
            int pad = w / 2;
            int prevStart = toolCarousel.getPaddingStart();
            if (!carouselSidePaddingApplied || prevStart != pad) {
                carouselSidePaddingApplied = true;
                toolCarousel.setPaddingRelative(pad, toolCarousel.getPaddingTop(), pad, toolCarousel.getPaddingBottom());
                int pos = toolAdapter.positionForTool(currentTool.ordinal());
                toolCarousel.post(() -> centerCarouselOnPosition(pos, false));
            }
        });

        // Update currentTool when user scrolls to a different tool
        toolCarousel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (isProgrammaticScroll) return;
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapView = toolSnapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        int toolIdx = position % PresentationTool.values().length;
                        if (toolIdx != currentTool.ordinal()) {
                            currentTool = PresentationTool.values()[toolIdx];
                            saveState();
                            updateToolHint();
                            toolAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        });

        // Set timer label and total text based on mode
        updateToolHint();
        updateTimerLabel();
        updateBlackScreenButton();
        if (timerCardContainer != null) {
            timerCardContainer.post(this::updateTimerProgressFill);
        }
    }

    private void setupListeners() {
        // No separate listeners needed — tool selection handled by carousel snap + item clicks

        // Next button - send right arrow
        btnNext.setOnClickListener(v -> {
            vibrate();
            sendRightArrowKey();
        });
        btnNext.setOnLongClickListener(v -> {
            vibrate();
            sendKeyHID(KEY_END);
            return true;
        });

        // Previous button - send left arrow
        btnPrevious.setOnClickListener(v -> {
            vibrate();
            sendLeftArrowKey();
        });
        btnPrevious.setOnLongClickListener(v -> {
            vibrate();
            sendKeyHID(KEY_HOME);
            return true;
        });

        // Play button - Opt+Cmd+P for Keynote play, ESC to stop
        btnPlay.setOnClickListener(v -> {
            vibrate();
            togglePlay();
        });

        // Black screen toggle
        btnBlackScreen.setOnClickListener(v -> {
            if (!playActive) {
                return;
            }
            vibrate();
            toggleBlackScreen();
        });

        // App switcher button (Cmd+Tab) — supports stateful macOS app switcher:
        //   1st tap  → Cmd down + Tab down + Tab up  (opens switcher, Cmd held)
        //   next taps → Tab down + Tab up              (cycles apps, Cmd still held)
        //   double tap → release all                    (confirms selection)
        GestureDetector switcherGesture = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (appSwitcherActive) {
                            cycleAppSwitcher();
                        } else {
                            openAppSwitcher();
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (appSwitcherActive) {
                            closeAppSwitcher();
                        }
                        return true;
                    }
                });

        btnLaser.setOnTouchListener((v, event) -> {
            switcherGesture.onTouchEvent(event);
            return true;
        });

        // Pointer mode - open touchpad dialog
        btnPointer.setOnClickListener(v -> {
            vibrate();
            showTouchpadDialog();
        });

        // Timer section gestures:
        // single tap = start/pause, double tap = reset (restart if running), long press = settings.
        if (timerCardContainer != null) {
            GestureDetector timerGestureDetector = new GestureDetector(requireContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            vibrate();
                            if (timerRunning) {
                                stopTimer();
                            } else {
                                startTimer();
                            }
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            vibrate();
                            boolean wasRunning = timerRunning;
                            resetTimer();
                            if (wasRunning) {
                                startTimer();
                            }
                            return true;
                        }

                        @Override
                        public void onLongPress(MotionEvent e) {
                            vibrate();
                            showEditTimerDialog();
                        }
                    });

            timerCardContainer.setOnTouchListener((v, event) -> timerGestureDetector.onTouchEvent(event));
        }
    }

    private void setupGestureDetector(View view) {
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Horizontal swipe
                    if (Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right = previous
                            sendLeftArrowKey();
                        } else {
                            // Swipe left = next
                            sendRightArrowKey();
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Tap = next
                sendRightArrowKey();
                return true;
            }
        });

        // Attach gesture detector to the root view for swipe and tap
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                return gestureDetector.onTouchEvent(event);
            });
        }
    }

    // ── Slide Navigation (simplified) ─────────────────────────────────

    private void sendKeyHID(int keyCode) {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, keyCode);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
            }
        }
    }

    private void sendRightArrowKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, KEY_RIGHT_ARROW);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.v(TAG, "Sent right arrow key");
            } else {
                Log.w(TAG, "Not connected - cannot send right arrow key");
            }
        }
    }

    private void sendLeftArrowKey() {
        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyEvent(0, KEY_LEFT_ARROW);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
                Log.v(TAG, "Sent left arrow key");
            } else {
                Log.w(TAG, "Not connected - cannot send left arrow key");
            }
        }
    }

    // ── Application Switcher (Cmd+Tab state machine) ─────────────────

    private int getAppSwitcherModifier() {
        if (getActivity() instanceof MainActivity) {
            String os = ((MainActivity) getActivity()).getTargetOs();
            if ("windows".equals(os) || "linux".equals(os)) {
                return 0x04; // Alt modifier bitmask
            }
        }
        return 0x08; // Cmd modifier bitmask (macOS default)
    }

    private void openAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            // If presentation is playing, stop it first
            if (playActive) {
                togglePlay();
                timerHandler.postDelayed(() -> openAppSwitcher(), 300);
                return;
            }
            int mod = getAppSwitcherModifier();
            // Press Cmd + Tab
            cm.sendKeyEvent(mod, KEY_TAB);
            // Release Tab after short delay (Cmd stays held)
            timerHandler.postDelayed(() -> {
                ConnectionManager c = getConnectionManager();
                if (c != null && c.isConnected()) {
                    c.sendRawHIDReport(mod, 0);
                }
            }, 100);
            appSwitcherActive = true;
            btnLaser.setTextColor(ContextCompat.getColor(requireContext(), R.color.presentation_green));
            Log.v(TAG, "App switcher opened (modifier=" + mod + " held)");
            // Safety timeout: auto-release after 10s of inactivity
            timerHandler.postDelayed(() -> {
                if (appSwitcherActive) {
                    closeAppSwitcher();
                    Log.v(TAG, "App switcher auto-released (timeout)");
                }
            }, 3000);
        }
    }

    private void cycleAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            int mod = getAppSwitcherModifier();
            // Press Tab (modifier already held from previous step)
            cm.sendKeyEvent(mod, KEY_TAB);
            // Release Tab after short delay (Cmd stays held)
            timerHandler.postDelayed(() -> {
                ConnectionManager c = getConnectionManager();
                if (c != null && c.isConnected()) {
                    c.sendRawHIDReport(mod, 0);
                }
            }, 100);
            Log.v(TAG, "App switcher cycled");
        }
    }

    private void closeAppSwitcher() {
        vibrate();
        ConnectionManager cm = getConnectionManager();
        if (cm != null && cm.isConnected()) {
            cm.sendKeyRelease();
            appSwitcherActive = false;
            btnLaser.setTextColor(ContextCompat.getColor(requireContext(), R.color.presentation_button_content)); // theme default = inactive
            Log.v(TAG, "App switcher closed (all released)");
        }
    }

    private ConnectionManager getConnectionManager() {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).getConnectionManager();
        }
        return null;
    }

    private void showTouchpadDialog() {
        if (popOutTouchPad == null) {
            popOutTouchPad = new PopOutTouchPadDialog(this, true);
        }
        popOutTouchPad.show();
    }

    // ── Black Screen ─────────────────────────────────────────────────

    private void togglePlay() {
        playActive = !playActive;
        updatePlayButton();

        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                if (playActive) {
                    int mod = currentTool.playModifier;
                    int key = currentTool.playKey;
                    if (key != 0) {
                        cm.sendKeyEvent(mod, key);
                        timerHandler.postDelayed(() -> {
                            ConnectionManager c = getConnectionManager();
                            if (c != null && c.isConnected()) c.sendKeyRelease();
                        }, 100);
                    }
                    Log.v(TAG, "Play: " + currentTool.name() + " keys sent");
                } else {
                    cm.sendKeyEvent(0, currentTool.stopKey);
                    timerHandler.postDelayed(() -> {
                        ConnectionManager c = getConnectionManager();
                        if (c != null && c.isConnected()) c.sendKeyRelease();
                    }, 100);
                    Log.v(TAG, "Stop: " + currentTool.stopKey + " sent");
                }
            }
        }
    }

    private void updatePlayButton() {
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.presentation_button_content);
        if (playActive) {
            btnPlay.setText(R.string.stop);
            btnPlay.setBackgroundResource(R.drawable.presentation_active_dark_button);
            btnPlay.setTextColor(textPrimary);
            btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            btnPlay.setText(R.string.play);
            btnPlay.setBackgroundResource(R.drawable.presentation_primary_button);
            btnPlay.setTextColor(textPrimary);
            btnPlay.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);

            // Hide-screen action is valid only while presentation is active.
            if (blackScreenActive) {
                blackScreenActive = false;
            }
        }
        updateBlackScreenButton();
    }

    private void selectTool(PresentationTool tool) {
        vibrate();
        currentTool = tool;
        saveState();
        updateToolHint();
        toolAdapter.notifyDataSetChanged();
        scrollToCurrentTool();
    }

    private void updateToolHint() {
        if (hintPlay != null) {
            hintPlay.setText(getString(currentTool.shortcutHintRes));
        }
    }

    private void scrollToCurrentTool() {
        int target = toolAdapter.positionForTool(currentTool.ordinal());
        centerCarouselOnPosition(target, true);
    }

    /**
     * Places the item at {@code adapterPosition} in the horizontal center of the carousel.
     * {@link LinearSnapHelper} only snaps after user flings; programmatic {@link RecyclerView#scrollToPosition}
     * aligns to an edge, so we apply the snap offset explicitly.
     */
    private void centerCarouselOnPosition(int adapterPosition, boolean smooth) {
        if (toolCarousel == null || toolSnapHelper == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) toolCarousel.getLayoutManager();
        if (lm == null) return;

        isProgrammaticScroll = true;
        toolCarousel.scrollToPosition(adapterPosition);

        Runnable finishProgrammatic = () -> isProgrammaticScroll = false;

        Runnable applySnap = new Runnable() {
            int retries;

            @Override
            public void run() {
                View child = lm.findViewByPosition(adapterPosition);
                if (child == null && retries++ < 8) {
                    toolCarousel.post(this);
                    return;
                }
                if (child == null) {
                    finishProgrammatic.run();
                    return;
                }
                int[] dist = toolSnapHelper.calculateDistanceToFinalSnap(lm, child);
                if (dist == null || dist[0] == 0) {
                    finishProgrammatic.run();
                    return;
                }
                if (smooth) {
                    toolCarousel.smoothScrollBy(dist[0], 0);
                    toolCarousel.postDelayed(finishProgrammatic, 450);
                } else {
                    toolCarousel.scrollBy(dist[0], 0);
                    finishProgrammatic.run();
                }
            }
        };
        toolCarousel.post(applySnap);
    }

    /**
     * Infinite carousel adapter. Uses a large virtual range so scrolling
     * wraps seamlessly via modular indexing.
     */
    private class ToolCarouselAdapter extends RecyclerView.Adapter<ToolCarouselAdapter.ViewHolder> {
        private static final int VIRTUAL_REPEAT = 10000;
        private final PresentationTool[] tools;
        private final java.util.function.Consumer<PresentationTool> onSelect;

        ToolCarouselAdapter(PresentationTool[] tools, java.util.function.Consumer<PresentationTool> onSelect) {
            this.tools = tools;
            this.onSelect = onSelect;
        }

        int positionForTool(int toolOrdinal) {
            // Start near the middle of the virtual range, aligned to tool index
            return (VIRTUAL_REPEAT / 2) * tools.length + toolOrdinal;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(requireContext());
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(32, 8, 32, 8);
            tv.setAllCaps(false);
            tv.setBackgroundResource(android.R.color.transparent);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PresentationTool tool = tools[position % tools.length];
            TextView tv = (TextView) holder.itemView;
            tv.setText(getString(tool.nameRes));
            boolean selected = (tool == currentTool);
            tv.setTextSize(selected ? 20 : 16);
            tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            int colorRes = selected ? R.color.presentation_text_primary : R.color.presentation_text_muted;
            tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        }

        @Override
        public int getItemCount() {
            return tools.length * VIRTUAL_REPEAT;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        onSelect.accept(tools[pos % tools.length]);
                    }
                });
            }
        }
    }

    private void toggleBlackScreen() {
        blackScreenActive = !blackScreenActive;
        updateBlackScreenButton();

        if (getActivity() instanceof MainActivity) {
            ConnectionManager cm = ((MainActivity) getActivity()).getConnectionManager();
            if (cm != null && cm.isConnected()) {
                // Send 'B' key to toggle black screen in most presentation software
                cm.sendKeyEvent(0, KEY_B);
                timerHandler.postDelayed(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ConnectionManager c = ((MainActivity) getActivity()).getConnectionManager();
                        if (c != null && c.isConnected()) {
                            c.sendKeyRelease();
                        }
                    }
                }, 100);
            }
        }

        Log.v(TAG, "Black screen: " + blackScreenActive);
    }

    private void updateBlackScreenButton() {
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.presentation_button_content);
        btnBlackScreen.setEnabled(playActive);
        btnBlackScreen.setAlpha(playActive ? 1f : 0.55f);
        if (blackScreenActive) {
            btnBlackScreen.setBackgroundResource(R.drawable.presentation_active_dark_button);
            btnBlackScreen.setTextColor(textPrimary);
            btnBlackScreen.setText(R.string.show_screen_multiline);
            btnBlackScreen.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else {
            btnBlackScreen.setBackgroundResource(R.drawable.presentation_action_button);
            btnBlackScreen.setTextColor(textPrimary);
            btnBlackScreen.setText(R.string.hide_screen_multiline);
            btnBlackScreen.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    // ── Application Switcher (Command+Tab) already implemented above ──

    // ── Timer ────────────────────────────────────────────────────────

    private void showEditTimerDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_timer, null);
        EditText editText = dialogView.findViewById(R.id.edit_timer_minutes);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_timer_mode);
        View preset5 = dialogView.findViewById(R.id.btn_preset_5);
        View preset10 = dialogView.findViewById(R.id.btn_preset_10);
        View preset15 = dialogView.findViewById(R.id.btn_preset_15);
        View preset20 = dialogView.findViewById(R.id.btn_preset_20);
        View preset30 = dialogView.findViewById(R.id.btn_preset_30);
        View preset60 = dialogView.findViewById(R.id.btn_preset_60);

        int currentMinutes = timerDuration / 60;
        editText.setText(String.valueOf(currentMinutes));
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        View.OnClickListener applyPresetMinutes = v -> {
            int minutes = 0;
            int id = v.getId();
            if (id == R.id.btn_preset_5) minutes = 5;
            else if (id == R.id.btn_preset_10) minutes = 10;
            else if (id == R.id.btn_preset_15) minutes = 15;
            else if (id == R.id.btn_preset_20) minutes = 20;
            else if (id == R.id.btn_preset_30) minutes = 30;
            else if (id == R.id.btn_preset_60) minutes = 60;

            if (minutes > 0) {
                editText.setText(String.valueOf(minutes));
                editText.setSelection(editText.getText().length());
            }
        };

        if (preset5 != null) preset5.setOnClickListener(applyPresetMinutes);
        if (preset10 != null) preset10.setOnClickListener(applyPresetMinutes);
        if (preset15 != null) preset15.setOnClickListener(applyPresetMinutes);
        if (preset20 != null) preset20.setOnClickListener(applyPresetMinutes);
        if (preset30 != null) preset30.setOnClickListener(applyPresetMinutes);
        if (preset60 != null) preset60.setOnClickListener(applyPresetMinutes);

        // Set current mode
        radioGroup.check(timerMode == TIMER_MODE_COUNTDOWN
                ? R.id.radio_countdown : R.id.radio_countup);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.presentation_timer_settings_title)
                .setView(dialogView)
                .setPositiveButton(R.string.pace_ok, (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        int newMinutes = Integer.parseInt(input);
                        if (newMinutes > 0) {
                            timerDuration = newMinutes * 60;
                            int selectedMode = radioGroup.getCheckedRadioButtonId() == R.id.radio_countdown
                                    ? TIMER_MODE_COUNTDOWN : TIMER_MODE_COUNTUP;
                            timerMode = selectedMode;

                            if (timerMode == TIMER_MODE_COUNTDOWN) {
                                timerRemainingSec = timerDuration;
                                countdownOvertime = false;
                            } else {
                                timerRemainingSec = 0;
                                countdownOvertime = false;
                            }
                            timerTargetAlerted = false;
                            timerRunning = false;
                            timerHandler.removeCallbacks(timerRunnable);
                            updateTimerDisplay();
                            updateTimerLabel();
                            saveState();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startTimer() {
        if (timerRunning) return;
        timerRunning = true;
        timerStartTime = System.currentTimeMillis();
        updateTimerProgressFill();
        saveState();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (timerRunning) {
                    if (timerMode == TIMER_MODE_COUNTDOWN) {
                        if (!countdownOvertime) {
                            if (timerRemainingSec > 0) {
                                timerRemainingSec--;
                            }
                            if (timerRemainingSec <= 0) {
                                timerRemainingSec = 0;
                                countdownOvertime = true;
                                triggerTimerTargetAlertIfNeeded();
                            }
                        } else {
                            // Continue in opposite direction after 00:00 (overtime count up).
                            timerRemainingSec++;
                        }
                    } else {
                        // Countup mode (never auto-stops at target time).
                        timerRemainingSec++;
                        if (timerRemainingSec >= timerDuration) {
                            triggerTimerTargetAlertIfNeeded();
                        }
                    }

                    updateTimerDisplay();
                    timerHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
                    saveState();
                }
            }
        };

        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerRunning = false;
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        updateTimerProgressFill();
        saveState();
    }

    private void resetTimer() {
        stopTimer();
        if (timerMode == TIMER_MODE_COUNTDOWN) {
            timerRemainingSec = timerDuration;
            countdownOvertime = false;
        } else {
            timerRemainingSec = 0;
            countdownOvertime = false;
        }
        timerTargetAlerted = false;
        updateTimerDisplay();
        saveState();
    }

    private void updateTimerDisplay() {
        if (timerMode == TIMER_MODE_COUNTDOWN && countdownOvertime) {
            timerRemaining.setText("+" + formatTime(timerRemainingSec));
        } else {
            timerRemaining.setText(formatTime(timerRemainingSec));
        }

        boolean warningActive = (timerMode == TIMER_MODE_COUNTUP && timerRemainingSec >= timerDuration)
                || (timerMode == TIMER_MODE_COUNTDOWN && countdownOvertime);
        int colorRes = warningActive ? R.color.presentation_red : R.color.presentation_text_primary;
        timerRemaining.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        updateTimerProgressFill();
        updateTimerHeaderContrast();
    }

    private void updateTimerLabel() {
        if (timerStatusText == null) return;
        String modeText;
        if (timerMode == TIMER_MODE_COUNTDOWN) {
            modeText = getString(R.string.remaining);
        } else {
            modeText = getString(R.string.elapsed);
        }
        timerStatusText.setText(getString(
                R.string.presentation_timer_status_format, modeText, formatTime(timerDuration)));
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    // ── Persistence ──────────────────────────────────────────────────

    private void loadState() {
        timerDuration = prefs.getInt(PREF_TIMER_DURATION, DEFAULT_TIMER_DURATION);
        timerRemainingSec = prefs.getInt(PREF_TIMER_REMAINING, DEFAULT_TIMER_DURATION);
        timerMode = prefs.getInt(PREF_TIMER_MODE, TIMER_MODE_COUNTDOWN);
        countdownOvertime = prefs.getBoolean(PREF_TIMER_COUNTDOWN_OVERTIME, false);
        timerRunning = prefs.getBoolean(PREF_TIMER_RUNNING, false);
        blackScreenActive = prefs.getBoolean(PREF_BLACK_SCREEN, false);
        int toolIdx = prefs.getInt(PREF_TOOL_INDEX, 0);
        currentTool = PresentationTool.values()[toolIdx % PresentationTool.values().length];

        if (timerMode == TIMER_MODE_COUNTUP) {
            timerTargetAlerted = timerRemainingSec >= timerDuration;
        } else {
            timerTargetAlerted = countdownOvertime;
        }
    }

    private void saveState() {
        prefs.edit()
            .putInt(PREF_TIMER_DURATION, timerDuration)
            .putInt(PREF_TIMER_REMAINING, timerRemainingSec)
            .putInt(PREF_TIMER_MODE, timerMode)
            .putBoolean(PREF_TIMER_COUNTDOWN_OVERTIME, countdownOvertime)
            .putBoolean(PREF_TIMER_RUNNING, timerRunning)
            .putBoolean(PREF_BLACK_SCREEN, blackScreenActive)
            .putInt(PREF_TOOL_INDEX, currentTool.ordinal())
            .apply();
    }

    private void triggerTimerTargetAlertIfNeeded() {
        if (timerTargetAlerted) return;
        timerTargetAlerted = true;
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { vibrator.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE)); } else { vibrator.vibrate(220); };
        }
    }

    private void updateTimerProgressFill() {
        if (timerCardContainer == null || timerProgressFill == null) return;

        int totalWidth = timerCardContainer.getWidth();
        int totalHeight = timerCardContainer.getHeight();
        if (totalWidth <= 0 || totalHeight <= 0) {
            timerCardContainer.post(this::updateTimerProgressFill);
            return;
        }

        float fraction = 0f;
        boolean fromStart = true;

        if (timerMode == TIMER_MODE_COUNTDOWN) {
            fromStart = false;
            if (countdownOvertime) {
                fraction = 0f;
            } else if (timerDuration > 0) {
                fraction = Math.max(0f, Math.min(1f, timerRemainingSec / (float) timerDuration));
            }

            // Keep clean card before first start in countdown mode.
            if (!timerRunning && !countdownOvertime && timerRemainingSec == timerDuration) {
                fraction = 0f;
            }
        } else {
            fromStart = true;
            if (timerDuration > 0) {
                fraction = Math.max(0f, Math.min(1f, timerRemainingSec / (float) timerDuration));
            }
            if (timerRemainingSec >= timerDuration) {
                fraction = 1f;
            }

            // Keep clean card before first start in count-up mode.
            if (!timerRunning && timerRemainingSec == 0) {
                fraction = 0f;
            }
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) timerProgressFill.getLayoutParams();
        params.width = totalWidth;
        params.height = totalHeight;
        params.gravity = fromStart ? Gravity.START : Gravity.END;
        timerProgressFill.setLayoutParams(params);
        timerProgressFill.setPivotX(fromStart ? 0f : totalWidth);
        timerProgressFill.setScaleX(fraction);
        timerProgressFill.setVisibility(fraction > 0f ? View.VISIBLE : View.INVISIBLE);
        // Opaque colorPrimary with view alpha: running ~40%, paused ~20% effective tint.
        timerProgressFill.setAlpha(timerRunning ? 0.4f : 0.2f);
    }

    private void updateTimerHeaderContrast() {
        if (timerHeaderLabel == null) return;
        boolean useOnFill = timerRunning;
        timerHeaderLabel.setTextColor(ContextCompat.getColor(
                requireContext(),
                useOnFill ? R.color.presentation_timer_header_on_fill : R.color.presentation_text_muted
        ));
        if (timerStatusText != null) {
            timerStatusText.setTextColor(ContextCompat.getColor(
                    requireContext(),
                    useOnFill ? R.color.presentation_timer_status_on_fill : R.color.presentation_text_secondary
            ));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)); } else { vibrator.vibrate(30); };
        }
    }

    private void ensurePresentationPortraitPairListenerEnabled() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (portraitPairOrientationListener == null) {
            portraitPairOrientationListener =
                    new OrientationEventListener(
                            activity.getApplicationContext(), SensorManager.SENSOR_DELAY_NORMAL) {
                        @Override
                        public void onOrientationChanged(int orientation) {
                            Activity a = getActivity();
                            if (!isAdded() || a == null) {
                                return;
                            }
                            updatePresentationPortraitLockFromSensorDegrees(orientation);
                        }
                    };
        }
        if (!portraitPairOrientationListener.canDetectOrientation()) {
            lastPortraitPairLock = Integer.MIN_VALUE;
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            return;
        }
        syncPresentationPortraitLockFromDisplayRotation();
        portraitPairOrientationListener.enable();
    }

    private void disablePresentationPortraitPairListener() {
        if (portraitPairOrientationListener != null) {
            portraitPairOrientationListener.disable();
        }
        lastPortraitPairLock = Integer.MIN_VALUE;
    }

    private void syncPresentationPortraitLockFromDisplayRotation() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        int lock;
        switch (displayRotationCompat(activity)) {
            case Surface.ROTATION_180:
                lock = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            case Surface.ROTATION_0:
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
            default:
                lock = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
        }
        applyPresentationPortraitLockIfChanged(activity, lock);
    }

    private static int displayRotationCompat(@NonNull Activity activity) {
        // getDisplay() requires API 30+, use getDefaultDisplay() for older versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Display display = activity.getDisplay();
            if (display != null) {
                return display.getRotation();
            }
        }
        return activity.getWindowManager().getDefaultDisplay().getRotation();
    }

    private void updatePresentationPortraitLockFromSensorDegrees(int orientationDegrees) {
        if (orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        int lock;
        if (orientationDegrees >= 315 || orientationDegrees < 45) {
            lock = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if (orientationDegrees >= 135 && orientationDegrees < 225) {
            lock = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        } else {
            lock = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        applyPresentationPortraitLockIfChanged(activity, lock);
    }

    private void applyPresentationPortraitLockIfChanged(@NonNull Activity activity, int lock) {
        if (lock == lastPortraitPairLock) {
            return;
        }
        lastPortraitPairLock = lock;
        activity.setRequestedOrientation(lock);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onResume() {
        super.onResume();
        ensurePresentationPortraitPairListenerEnabled();
    }

    @Override
    public void onPause() {
        disablePresentationPortraitPairListener();
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        saveState();
        // Clean up app switcher state if navigating away
        if (appSwitcherActive) {
            ConnectionManager cm = getConnectionManager();
            if (cm != null && cm.isConnected()) {
                cm.sendKeyRelease();
            }
            appSwitcherActive = false;
            btnLaser.setTextColor(ContextCompat.getColor(requireContext(), R.color.presentation_button_content));
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (vibrator != null) vibrator.cancel();
        timerHandler.removeCallbacksAndMessages(null);
        if (popOutTouchPad != null) {
            popOutTouchPad.dismissIfShowing();
        }
    }
}
