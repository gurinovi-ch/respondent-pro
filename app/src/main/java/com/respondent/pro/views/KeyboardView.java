package com.respondent.pro.views;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.respondent.pro.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Self-contained on-screen keyboard for Android (RU / EN / numeric)
 * Extracted from OnLiker Android kiosk client, adapted for RESPONDENT.PRO
 */
public class KeyboardView extends FrameLayout implements View.OnClickListener {
    public static final int LAYOUT_RUS = 0;
    public static final int LAYOUT_ENG = 1;

    private static final long SHIFT_DOUBLE_PRESS_INTERVAL = 600;
    private long shiftLastPressTime;

    private boolean isShift = false;
    private boolean isShiftLong = false;
    private boolean isNumbersShowing = false;
    private EditText currentTextEdit;
    private int currentLayout = LAYOUT_RUS;
    private int currentShiftKey = R.id.t_rus_shift;

    private final List<Integer> rusTextKeys = new ArrayList<>(
        Arrays.asList(R.id.t_rus_q, R.id.t_rus_w, R.id.t_rus_e, R.id.t_rus_r, R.id.t_rus_t, R.id.t_rus_y, R.id.t_rus_u, R.id.t_rus_i,
                R.id.t_rus_o, R.id.t_rus_p, R.id.t_rus_hh, R.id.t_rus_ha, R.id.t_rus_a, R.id.t_rus_s, R.id.t_rus_d, R.id.t_rus_f,
                R.id.t_rus_g, R.id.t_rus_h, R.id.t_rus_j, R.id.t_rus_k, R.id.t_rus_l, R.id.t_rus_zz, R.id.t_rus_zh, R.id.t_rus_z,
                R.id.t_rus_x, R.id.t_rus_c, R.id.t_rus_v, R.id.t_rus_b, R.id.t_rus_n, R.id.t_rus_m, R.id.t_rus_bb, R.id.t_rus_yu)
    );

    private final List<Integer> rusSystemKeys = new ArrayList<>(
            Arrays.asList(R.id.t_rus_backspace, R.id.t_rus_shift, R.id.t_rus_123, R.id.t_rus_lang,
                          R.id.t_rus_space, R.id.t_rus_dot, R.id.t_rus_comma, R.id.t_rus_enter)
    );

    private final List<Integer> engTextKeys = new ArrayList<>(
        Arrays.asList(R.id.t_eng_q, R.id.t_eng_w, R.id.t_eng_e, R.id.t_eng_r, R.id.t_eng_t, R.id.t_eng_y, R.id.t_eng_u, R.id.t_eng_i, R.id.t_eng_o,
                      R.id.t_eng_p, R.id.t_eng_a, R.id.t_eng_s, R.id.t_eng_d, R.id.t_eng_f, R.id.t_eng_g, R.id.t_eng_h, R.id.t_eng_j, R.id.t_eng_k,
                      R.id.t_eng_l, R.id.t_eng_z, R.id.t_eng_x, R.id.t_eng_c, R.id.t_eng_v, R.id.t_eng_b, R.id.t_eng_n, R.id.t_eng_m)
    );

    private final List<Integer> engSystemKeys = new ArrayList<>(
            Arrays.asList(R.id.t_eng_shift, R.id.t_eng_backspace, R.id.t_eng_123, R.id.t_eng_lang, R.id.t_eng_at,
                          R.id.t_eng_space, R.id.t_eng_dot, R.id.t_eng_comma, R.id.t_eng_enter)
    );

    private final List<Integer> numTextKeys = new ArrayList<>(
            Arrays.asList(R.id.t_num_bang, R.id.t_num_at, R.id.t_num_sharp, R.id.t_num_plus, R.id.t_num_1, R.id.t_num_2, R.id.t_num_3,
                    R.id.t_num_asterisk, R.id.t_num_amp, R.id.t_num_open_paren, R.id.t_close_paren, R.id.t_num_til, R.id.t_num_am,
                    R.id.t_num_hat, R.id.t_num_minus, R.id.t_num_4, R.id.t_num_5, R.id.t_num_6, R.id.t_num_slash, R.id.t_num_colon,
                    R.id.t_num_semicolon, R.id.t_num_quote, R.id.t_num_angle_bracket_left, R.id.t_num_angle_bracket_right, R.id.t_num_equals,
                    R.id.t_num_7, R.id.t_num_8, R.id.t_num_9, R.id.t_num_0, R.id.t_num_underscore, R.id.t_num_question, R.id.t_num_comma,
                    R.id.t_num_space, R.id.t_num_dot));

    private final List<Integer> numSystemKeys = new ArrayList<>(
            Arrays.asList(R.id.t_num_back, R.id.t_num_backspace, R.id.t_num_lang, R.id.t_num_enter));

    private List<Integer> currentTextKeys = rusTextKeys;

    public KeyboardView(Context context) {
        super(context);
        init();
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.keyboard, this);
        initViews();
    }

    private void initViews() {
        for (int keyId : rusTextKeys) {
            $(keyId).setOnClickListener(this);
        }
        for (int keyId : rusSystemKeys) {
            $(keyId).setOnClickListener(this);
        }
        for (int keyId : engTextKeys) {
            $(keyId).setOnClickListener(this);
        }
        for (int keyId : engSystemKeys) {
            $(keyId).setOnClickListener(this);
        }
        for (int keyId : numTextKeys) {
            $(keyId).setOnClickListener(this);
        }
        for (int keyId : numSystemKeys) {
            $(keyId).setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (currentTextEdit == null) return;

        int cursorPosition = currentTextEdit.getSelectionStart();
        int cursorEndPosition = currentTextEdit.getSelectionEnd();

        // handle character button click
        if (v.getTag() != null && "letter_button".equals(v.getTag())) {
            CharSequence text = ((Button) v).getText();
            if (isShift) {
                if (!isShiftLong) {
                    isShift = false;
                    makeAllTextButtonsBig(isShift);
                }
                text = text.toString().toUpperCase();
            }

            if (cursorPosition == cursorEndPosition) {
                currentTextEdit.getText().insert(cursorPosition, text);
            } else {
                currentTextEdit.getText().replace(cursorPosition, cursorEndPosition, text);
                currentTextEdit.setSelection(cursorPosition + text.length());
            }
            // MUST be after text insertion — Compose reads EditText text here
            callOnClick();
            return;
        }

        int id = v.getId();
        if (id == R.id.t_rus_shift || id == R.id.t_eng_shift) {
            handleShiftClick();
        } else if (id == R.id.t_rus_123 || id == R.id.t_eng_123) {
            enableNumbers();
        } else if (id == R.id.t_num_back) {
            disableNumbers();
        } else if (id == R.id.t_rus_lang || id == R.id.t_eng_lang || id == R.id.t_num_lang) {
            handleChangeLangClick();
        } else if (id == R.id.t_rus_backspace || id == R.id.t_eng_backspace || id == R.id.t_num_backspace) {
            Editable editable = currentTextEdit.getText();
            int charCount = editable.length();
            if (charCount > 0 && cursorPosition > 0) {
                if (cursorPosition == cursorEndPosition)
                    editable.delete(cursorPosition - 1, cursorEndPosition);
                else
                    editable.delete(cursorPosition, cursorEndPosition);
            }
        } else if (id == R.id.t_rus_enter || id == R.id.t_eng_enter || id == R.id.t_num_enter) {
            if (currentTextEdit.getInputType() != InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)
                currentTextEdit.append("\n");
        }
        // MUST be after all text modifications — Compose reads EditText text here
        callOnClick();
    }

    private void handleShiftClick() {
        long pressTime = System.currentTimeMillis();

        if (pressTime - shiftLastPressTime <= SHIFT_DOUBLE_PRESS_INTERVAL) {
            isShift = true;
            enableCapsLock();
        } else {
            isShift = !isShift;
            disableCapsLock();
            makeAllTextButtonsBig(isShift);
        }
        shiftLastPressTime = pressTime;
    }

    private void disableCapsLock() {
        Button b = $(currentShiftKey);
        isShiftLong = false;
        b.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.keyboard_button_system_bg));
    }

    private void enableCapsLock() {
        Button b = $(currentShiftKey);
        isShiftLong = true;
        b.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.keyboard_button_shift_pressed_bg));
    }

    private void disableShift() {
        isShift = false;
        makeAllTextButtonsBig(isShift);
    }

    private void handleChangeLangClick() {
        isNumbersShowing = false;
        disableCapsLock();
        disableShift();

        if (currentLayout == LAYOUT_RUS) {
            currentLayout = LAYOUT_ENG;
        } else if (currentLayout == LAYOUT_ENG) {
            currentLayout = LAYOUT_RUS;
        }
        setVisibilityForLang();
    }

    private void setVisibilityForLang() {
        if (currentLayout == LAYOUT_RUS) {
            currentShiftKey = R.id.t_rus_shift;
            $(R.id.english_keyboard).setVisibility(View.GONE);
            $(R.id.numeric_keyboard).setVisibility(View.GONE);
            $(R.id.russian_keyboard).setVisibility(View.VISIBLE);
            currentTextKeys = rusTextKeys;
        } else if (currentLayout == LAYOUT_ENG) {
            currentShiftKey = R.id.t_eng_shift;
            $(R.id.numeric_keyboard).setVisibility(View.GONE);
            $(R.id.russian_keyboard).setVisibility(View.GONE);
            $(R.id.english_keyboard).setVisibility(View.VISIBLE);
            currentTextKeys = engTextKeys;
        }
    }

    public void enableNumbers() {
        isNumbersShowing = true;
        $(R.id.english_keyboard).setVisibility(View.GONE);
        $(R.id.russian_keyboard).setVisibility(View.GONE);
        $(R.id.numeric_keyboard).setVisibility(View.VISIBLE);
        disableShift();
    }

    private void disableNumbers() {
        isNumbersShowing = false;
        setVisibilityForLang();
    }

    private void makeAllTextButtonsBig(boolean big) {
        for (int keyId : currentTextKeys) {
            Button b = $(keyId);
            b.setAllCaps(big);
        }
    }

    public void setInputText(EditText editText) {
        currentTextEdit = editText;
        switch (currentTextEdit.getInputType()) {
            case InputType.TYPE_CLASS_PHONE:
                enableNumbers();
                break;
            default:
                currentLayout = LAYOUT_RUS;
                setVisibilityForLang();
                break;
        }
    }

    public void setLayout(int layout) {
        if (layout == LAYOUT_ENG || layout == LAYOUT_RUS) {
            currentLayout = layout;
            setVisibilityForLang();
        }
    }

    protected <T extends View> T $(int id) {
        return (T) super.findViewById(id);
    }
}
