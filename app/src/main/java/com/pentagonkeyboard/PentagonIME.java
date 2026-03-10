package com.pentagonkeyboard;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PentagonIME extends InputMethodService
        implements PentagonKeyboardView.KeyListener,
                   SpellCheckerSession.SpellCheckerSessionListener {

    private View inputView;
    private PentagonKeyboardView keyboardView;
    private SidePanel sidePanel;
    private TextView suggestion0, suggestion1, suggestion2;

    private SpellCheckerSession spellSession;
    private TextServicesManager textServicesManager;
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private AudioManager audioManager;

    private final StringBuilder composingWord = new StringBuilder();
    private final List<String> currentSuggestions = new ArrayList<>();
    private boolean autoCapNext = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public View onCreateInputView() {
        inputView = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null);
        keyboardView = inputView.findViewById(R.id.keyboard_view);
        sidePanel    = inputView.findViewById(R.id.side_panel);
        keyboardView.setKeyListener(this);

        // Apply hand mode
        applyHandMode();

        suggestion0 = inputView.findViewById(R.id.suggestion_0);
        suggestion1 = inputView.findViewById(R.id.suggestion_1);
        suggestion2 = inputView.findViewById(R.id.suggestion_2);

        suggestion0.setOnClickListener(v -> acceptSuggestion(0));
        suggestion1.setOnClickListener(v -> acceptSuggestion(1));
        suggestion2.setOnClickListener(v -> acceptSuggestion(2));

        TextView spaceBar = inputView.findViewById(R.id.space_bar);
        spaceBar.setOnClickListener(v -> onSpace());

        clearSuggestions();
        return inputView;
    }

    private void applyHandMode() {
        String hand = prefs.getString("hand_mode", "left");
        keyboardView.setHandMode(hand);
        LinearLayout mainRow = inputView.findViewById(R.id.main_row);
        if ("right".equals(hand)) {
            // Move pentagon to right: remove both views, re-add side panel first
            mainRow.removeAllViews();
            LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f);
            LinearLayout.LayoutParams pentLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
            mainRow.addView(sidePanel, sideLp);
            mainRow.addView(keyboardView, pentLp);
        }
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        composingWord.setLength(0);
        currentSuggestions.clear();
        autoCapNext = prefs.getBoolean("auto_capitalize", true);
        if (autoCapNext && keyboardView != null) keyboardView.setShifted(true);
        if (prefs.getBoolean("predictive_text", true)) {
            textServicesManager = (TextServicesManager) getSystemService(TEXT_SERVICES_MANAGER_SERVICE);
            if (textServicesManager != null) {
                try {
                    spellSession = textServicesManager.newSpellCheckerSession(
                            null, Locale.getDefault(), this, true);
                } catch (Exception e) { spellSession = null; }
            }
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        commitComposing();
        if (spellSession != null) { spellSession.close(); spellSession = null; }
        clearSuggestions();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        composingWord.setLength(0);
        clearSuggestions();
    }

    // ── KeyListener ───────────────────────────────────────────────────
    @Override
    public void onKeyPress(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        doFeedback();
        boolean isLetter = key.length()==1 && Character.isLetter(key.charAt(0));
        if (isLetter) {
            composingWord.append(key);
            updateComposing(ic);
            requestSpellCheck();
        } else {
            commitComposing();
            ic.commitText(key, 1);
            clearSuggestions();
            if (prefs.getBoolean("auto_capitalize", true) &&
                (key.equals(".")||key.equals("!")||key.equals("?"))) {
                if (keyboardView!=null) keyboardView.setShifted(true);
            }
        }
    }

    @Override public void onDelete() {
        InputConnection ic = getCurrentInputConnection(); if(ic==null) return;
        doFeedback();
        if (composingWord.length()>0) {
            composingWord.deleteCharAt(composingWord.length()-1);
            if (composingWord.length()>0){ updateComposing(ic); requestSpellCheck(); }
            else { ic.commitText("",1); clearSuggestions(); }
        } else {
            CharSequence before=ic.getTextBeforeCursor(2,0);
            if(before!=null&&before.length()>0) ic.deleteSurroundingTextInCodePoints(1,0);
            clearSuggestions();
        }
    }

    @Override public void onSpace() {
        InputConnection ic = getCurrentInputConnection(); if(ic==null) return;
        doFeedback();
        if (prefs.getBoolean("double_space_period",true) && composingWord.length()==0) {
            CharSequence before=ic.getTextBeforeCursor(2,0);
            if(before!=null&&before.length()>=1&&before.charAt(before.length()-1)==' ') {
                ic.deleteSurroundingTextInCodePoints(1,0);
                ic.commitText(". ",1);
                if(prefs.getBoolean("auto_capitalize",true)&&keyboardView!=null) keyboardView.setShifted(true);
                return;
            }
        }
        if (prefs.getBoolean("autocorrect",true)&&composingWord.length()>0&&!currentSuggestions.isEmpty()) {
            String first=currentSuggestions.get(0);
            if(!first.equalsIgnoreCase(composingWord.toString())) {
                ic.commitText(first+" ",1); composingWord.setLength(0); clearSuggestions(); return;
            }
        }
        commitComposing(); ic.commitText(" ",1); clearSuggestions();
        if(prefs.getBoolean("auto_capitalize",true)) {
            CharSequence before=ic.getTextBeforeCursor(3,0);
            if(before!=null&&before.length()>=2) {
                char prev=before.charAt(before.length()-2);
                if(prev=='.'||prev=='!'||prev=='?') if(keyboardView!=null) keyboardView.setShifted(true);
            }
        }
    }

    @Override public void onShiftToggle() {
        if(keyboardView!=null) keyboardView.setShifted(!keyboardView.isShifted());
    }

    @Override public void onModeToggle(String mode) {
        if("voice".equals(mode)) { startVoice(); return; }
        if(!"alpha".equals(mode)) { commitComposing(); clearSuggestions(); }
        if(keyboardView!=null) keyboardView.setMode(mode);
    }

    @Override public void onSwipeWord(List<String> suggestions, String raw) {
        if(!prefs.getBoolean("swipe_input",true)) return;
        InputConnection ic=getCurrentInputConnection(); if(ic==null) return;
        commitComposing();
        if(!suggestions.isEmpty()) {
            showSuggestions(suggestions);
            ic.commitText(suggestions.get(0)+" ",1);
        } else {
            ic.commitText(raw.toLowerCase()+" ",1);
        }
        clearSuggestions();
    }

    // ── Voice ─────────────────────────────────────────────────────────
    private void startVoice() {
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch(Exception ignored){}
    }

    // ── Feedback ──────────────────────────────────────────────────────
    private void doFeedback() {
        if(prefs.getBoolean("vibrate_on_keypress",true)&&vibrator!=null) {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(25,VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(25);
        }
        if(prefs.getBoolean("sound_on_keypress",false)&&audioManager!=null)
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD,-1);
    }

    // ── Composing ─────────────────────────────────────────────────────
    private void updateComposing(InputConnection ic) {
        SpannableString s=new SpannableString(composingWord.toString());
        s.setSpan(new UnderlineSpan(),0,s.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ic.setComposingText(s,1);
    }
    private void commitComposing() {
        if(composingWord.length()==0) return;
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.commitText(composingWord.toString(),1);
        composingWord.setLength(0);
    }
    private void requestSpellCheck() {
        if(!prefs.getBoolean("predictive_text",true)) return;
        if(spellSession!=null&&composingWord.length()>0)
            spellSession.getSuggestions(new TextInfo(composingWord.toString()),3);
    }

    // ── Suggestions ───────────────────────────────────────────────────
    private void acceptSuggestion(int index) {
        if(index>=currentSuggestions.size()) return;
        String word=currentSuggestions.get(index);
        InputConnection ic=getCurrentInputConnection(); if(ic==null) return;
        composingWord.setLength(0); ic.commitText(word,1); clearSuggestions();
    }
    private void clearSuggestions() {
        currentSuggestions.clear();
        if(suggestion0!=null){suggestion0.setText("");suggestion0.setVisibility(View.INVISIBLE);}
        if(suggestion1!=null){suggestion1.setText("");suggestion1.setVisibility(View.INVISIBLE);}
        if(suggestion2!=null){suggestion2.setText("");suggestion2.setVisibility(View.INVISIBLE);}
    }
    private void showSuggestions(List<String> suggestions) {
        if(!prefs.getBoolean("predictive_text",true)) return;
        currentSuggestions.clear();
        String typed=composingWord.toString();
        List<String> filtered=new ArrayList<>();
        for(String s:suggestions) if(!s.equalsIgnoreCase(typed)) filtered.add(s);
        String s0=filtered.size()>0?filtered.get(0):"";
        String s1=typed;
        String s2=filtered.size()>1?filtered.get(1):"";
        if(!s0.isEmpty()) currentSuggestions.add(s0);
        if(!s1.isEmpty()) currentSuggestions.add(s1);
        if(!s2.isEmpty()) currentSuggestions.add(s2);
        if(suggestion0!=null){suggestion0.setText(s0);suggestion0.setVisibility(s0.isEmpty()?View.INVISIBLE:View.VISIBLE);}
        if(suggestion1!=null){suggestion1.setText(s1);suggestion1.setVisibility(s1.isEmpty()?View.INVISIBLE:View.VISIBLE);}
        if(suggestion2!=null){suggestion2.setText(s2);suggestion2.setVisibility(s2.isEmpty()?View.INVISIBLE:View.VISIBLE);}
    }

    @Override public void onGetSuggestions(final SuggestionsInfo[] results) {
        if(results==null) return;
        final List<String> suggestions=new ArrayList<>();
        for(SuggestionsInfo info:results) {
            if(info!=null) { int count=info.getSuggestionsCount();
                for(int i=0;i<count&&suggestions.size()<3;i++) {
                    String s=info.getSuggestionAt(i); if(s!=null&&!s.isEmpty()) suggestions.add(s);
                }
            }
        }
        if(inputView!=null) inputView.post(()->showSuggestions(suggestions));
    }
    @Override public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] r) {}
}
