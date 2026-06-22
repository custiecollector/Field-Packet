package org.fieldpacket.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.fieldpacket.core.AprsTransmitPacket;
import org.fieldpacket.core.Ax25Frame;
import org.fieldpacket.core.Bell202AfskDecoder;
import org.fieldpacket.core.Bell202AfskModulator;
import org.fieldpacket.core.FieldPacketCodec;
import org.fieldpacket.core.FieldPacketMessage;
import org.fieldpacket.core.FieldPacketSamples;
import org.fieldpacket.core.InMemoryToneGenerator;
import org.fieldpacket.core.KissFrame;
import org.fieldpacket.core.MessageTemplate;
import org.fieldpacket.core.Pcm16Import;
import org.fieldpacket.core.RadioPathPreset;
import org.fieldpacket.core.RadioTestPlan;
import org.fieldpacket.core.TransportProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 4001;
    private static final int REQ_PCM_IMPORT = 5001;
    private static final int MAX_IMPORT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TEXT_FIELD_CHARS = 160;
    private static final int MAX_MESSAGE_BODY_CHARS = 2048;
    private static final int MAX_KISS_HEX_CHARS = KissFrame.MAX_HEX_TEXT_CHARS;
    private static final String PREFS_NAME = "fieldpacket_prefs_v1";
    private static final String CUSTOM_TEMPLATE_PREFIX = "custom_template_";
    private static final String OPS_PRESET_PREFIX = "ops_preset_";

    private static final int BG = Color.rgb(8, 13, 18);
    private static final int PANEL = Color.rgb(18, 27, 35);
    private static final int PANEL_2 = Color.rgb(24, 36, 46);
    private static final int TEXT = Color.rgb(232, 240, 244);
    private static final int MUTED = Color.rgb(159, 178, 190);
    private static final int ACCENT = Color.rgb(42, 157, 143);
    private static final int WARN = Color.rgb(229, 184, 102);

    private Spinner typeSpinner;
    private EditText idField;
    private EditText fromField;
    private EditText areaField;
    private EditText expiresField;
    private EditText priorityField;
    private EditText locationField;
    private EditText needsField;
    private EditText bodyField;
    private Spinner messageTemplateSpinner;
    private TextView messageTemplateDetailsView;
    private EditText rawField;
    private TextView resultView;
    private LinearLayout emergencyFields;

    private Spinner radioPathSpinner;
    private TextView radioPathDetailsView;
    private TextView txLevelLabel;
    private SeekBar txLevelSeek;
    private EditText leadInField;
    private EditText tailField;
    private EditText repeatField;
    private AudioTrack toneTrack;
    private AudioTrack afskTrack;

    private EditText aprsSourceField;
    private EditText aprsDestinationField;
    private EditText aprsPathField;
    private EditText aprsInfoField;

    private Spinner transportSpinner;
    private TextView transportDetailsView;
    private EditText kissHexField;
    private EditText rawSampleRateField;

    private TextView rxListenCard;
    private TextView rxLevelCard;
    private TextView rxSyncCard;
    private TextView rxPacketCard;
    private Button rxButton;
    private volatile boolean receiving;
    private Thread rxThread;
    private final RingSamples rxRing = new RingSamples(Bell202AfskDecoder.SAMPLE_RATE_HZ * 12);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setTitle("FieldPacket");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(16));
        scroll.addView(root);

        buildRxStatusCards(root);
        LinearLayout composePanel = panel();
        composePanel.addView(section("Compose"));

        typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(spinnerAdapter(new String[]{"Plaintext field bulletin", "Emergency broadcast format"}));
        styleSpinner(typeSpinner);
        composePanel.addView(typeSpinner);

        idField = input("Packet ID", nextId());
        fromField = input("From", "FIELD");
        areaField = input("Area", "LOCAL");
        expiresField = input("Expires", "");
        priorityField = input("Priority", "HIGH");
        locationField = input("Location", "");
        needsField = input("Needs", "");
        bodyField = multiline("Body", "Short field bulletin.");
        limit(bodyField, MAX_MESSAGE_BODY_CHARS);

        composePanel.addView(idField);
        composePanel.addView(fromField);
        composePanel.addView(areaField);
        composePanel.addView(expiresField);
        emergencyFields = new LinearLayout(this);
        emergencyFields.setOrientation(LinearLayout.VERTICAL);
        emergencyFields.addView(priorityField);
        emergencyFields.addView(locationField);
        emergencyFields.addView(needsField);
        composePanel.addView(emergencyFields);
        composePanel.addView(bodyField);

        TextView templateNote = label("Templates", 13, MUTED, Typeface.BOLD);
        templateNote.setPadding(0, 0, 0, dp(6));
        composePanel.addView(templateNote);
        messageTemplateSpinner = new Spinner(this);
        messageTemplateSpinner.setAdapter(spinnerAdapter(MessageTemplate.labels()));
        styleSpinner(messageTemplateSpinner);
        composePanel.addView(messageTemplateSpinner);
        messageTemplateDetailsView = label("", 13, MUTED, Typeface.NORMAL);
        messageTemplateDetailsView.setPadding(0, dp(6), 0, dp(8));
        composePanel.addView(messageTemplateDetailsView);

        LinearLayout templateButtonRow = new LinearLayout(this);
        templateButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        templateButtonRow.setGravity(Gravity.CENTER);
        Button loadTemplateButton = button("Load template");
        Button saveTemplateButton = button("Save custom");
        templateButtonRow.addView(loadTemplateButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        templateButtonRow.addView(saveTemplateButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        composePanel.addView(templateButtonRow);
        Button loadSavedTemplateButton = button("Load saved custom template");
        composePanel.addView(loadSavedTemplateButton);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        Button composeButton = button("Compose packet");
        Button sampleButton = button("Load sample");
        buttonRow.addView(composeButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        buttonRow.addView(sampleButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        composePanel.addView(buttonRow);
        root.addView(composePanel);

        LinearLayout radioPanel = panel();
        radioPanel.addView(section("Radio test / calibration"));

        radioPathSpinner = new Spinner(this);
        radioPathSpinner.setAdapter(spinnerAdapter(RadioPathPreset.labels()));
        styleSpinner(radioPathSpinner);
        radioPanel.addView(radioPathSpinner);

        radioPathDetailsView = label("", 13, MUTED, Typeface.NORMAL);
        radioPathDetailsView.setPadding(0, dp(6), 0, dp(8));
        radioPanel.addView(radioPathDetailsView);

        leadInField = numberInput("TX lead-in ms", "800");
        tailField = numberInput("TX tail ms", "400");
        repeatField = numberInput("Repeat count", "2");
        radioPanel.addView(leadInField);
        radioPanel.addView(tailField);
        radioPanel.addView(repeatField);

        txLevelLabel = label("TX level / gain: 55%", 14, TEXT, Typeface.BOLD);
        txLevelLabel.setPadding(0, dp(4), 0, dp(4));
        radioPanel.addView(txLevelLabel);
        txLevelSeek = new SeekBar(this);
        txLevelSeek.setMax(100);
        txLevelSeek.setProgress(55);
        radioPanel.addView(txLevelSeek);

        LinearLayout radioButtonRow = new LinearLayout(this);
        radioButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        radioButtonRow.setGravity(Gravity.CENTER);
        Button testPacketButton = button("Known-good packet");
        Button playToneButton = button("Play test tone");
        radioButtonRow.addView(testPacketButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        radioButtonRow.addView(playToneButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        radioPanel.addView(radioButtonRow);

        Button stopToneButton = button("Stop tone");
        radioPanel.addView(stopToneButton);

        TextView rxNote = label("Live receive controls are in APRS / AFSK receive.", 13, MUTED, Typeface.NORMAL);
        rxNote.setPadding(0, dp(8), 0, 0);
        radioPanel.addView(rxNote);
        root.addView(radioPanel);

        LinearLayout aprsPanel = panel();
        aprsPanel.addView(section("APRS / AFSK transmit"));
        TextView aprsNote = label("Transmit APRS over AX.25 UI frames with Bell 202 AFSK 1200. Uses the radio path controls above; audio is generated in memory and not stored.", 13, MUTED, Typeface.NORMAL);
        aprsNote.setPadding(0, 0, 0, dp(8));
        aprsPanel.addView(aprsNote);

        aprsSourceField = input("APRS source callsign-SSID", AprsTransmitPacket.DEFAULT_SOURCE);
        aprsDestinationField = input("APRS destination", AprsTransmitPacket.DEFAULT_DESTINATION);
        aprsPathField = input("APRS path", AprsTransmitPacket.DEFAULT_PATH);
        aprsInfoField = multiline("APRS information field", AprsTransmitPacket.infoFromFieldPacket(currentMessage()));
        limit(aprsInfoField, AprsTransmitPacket.MAX_INFO_CHARS);
        aprsInfoField.setMinLines(3);
        aprsPanel.addView(aprsSourceField);
        aprsPanel.addView(aprsDestinationField);
        aprsPanel.addView(aprsPathField);
        aprsPanel.addView(aprsInfoField);

        LinearLayout aprsButtonRow = new LinearLayout(this);
        aprsButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        aprsButtonRow.setGravity(Gravity.CENTER);
        Button loadAprsButton = button("Load from packet");
        Button composeAprsButton = button("Compose AX.25");
        aprsButtonRow.addView(loadAprsButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        aprsButtonRow.addView(composeAprsButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        aprsPanel.addView(aprsButtonRow);

        LinearLayout afskButtonRow = new LinearLayout(this);
        afskButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        afskButtonRow.setGravity(Gravity.CENTER);
        Button playAfskButton = button("Play AFSK TX");
        Button stopAfskButton = button("Stop AFSK TX");
        afskButtonRow.addView(playAfskButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        afskButtonRow.addView(stopAfskButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        aprsPanel.addView(afskButtonRow);
        root.addView(aprsPanel);


        LinearLayout transportPanel = panel();
        transportPanel.addView(section("Transport helpers: KISS / SDR / manual import"));
        TextView transportNote = label("Build or decode KISS frames, paste packet text, or import a WAV/raw PCM sample for decode.", 13, MUTED, Typeface.NORMAL);
        transportNote.setPadding(0, 0, 0, dp(8));
        transportPanel.addView(transportNote);

        transportSpinner = new Spinner(this);
        transportSpinner.setAdapter(spinnerAdapter(TransportProfile.labels()));
        styleSpinner(transportSpinner);
        transportPanel.addView(transportSpinner);
        transportDetailsView = label("", 13, MUTED, Typeface.NORMAL);
        transportDetailsView.setPadding(0, dp(6), 0, dp(8));
        transportPanel.addView(transportDetailsView);

        kissHexField = multiline("KISS frame hex / TNC log bytes", "");
        limit(kissHexField, MAX_KISS_HEX_CHARS);
        kissHexField.setMinLines(4);
        transportPanel.addView(kissHexField);
        LinearLayout kissButtonRow = new LinearLayout(this);
        kissButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        kissButtonRow.setGravity(Gravity.CENTER);
        Button loadKissButton = button("Load current AX.25 as KISS");
        Button decodeKissButton = button("Decode KISS hex");
        kissButtonRow.addView(loadKissButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        kissButtonRow.addView(decodeKissButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        transportPanel.addView(kissButtonRow);

        rawSampleRateField = numberInput("Raw PCM sample rate Hz", String.valueOf(Bell202AfskDecoder.SAMPLE_RATE_HZ));
        rawSampleRateField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        transportPanel.addView(rawSampleRateField);
        LinearLayout importButtonRow = new LinearLayout(this);
        importButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        importButtonRow.setGravity(Gravity.CENTER);
        Button pickPcmButton = button("Pick PCM/WAV file");
        Button decodeGeneratedPcmButton = button("Decode current AFSK PCM");
        importButtonRow.addView(pickPcmButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        importButtonRow.addView(decodeGeneratedPcmButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        transportPanel.addView(importButtonRow);
        TextView importHint = label("PCM import opens a user-chosen file through Android's system picker and decodes bytes in memory only. Raw PCM is signed 16-bit little-endian mono; WAV PCM may be mono/stereo and is normalized to the Bell 202 decoder rate.", 13, MUTED, Typeface.NORMAL);
        importHint.setPadding(0, dp(8), 0, 0);
        transportPanel.addView(importHint);

        TextView presetHint = label("Save current radio, APRS, and import settings as an ops preset.", 13, MUTED, Typeface.NORMAL);
        presetHint.setPadding(0, dp(8), 0, dp(4));
        transportPanel.addView(presetHint);
        LinearLayout presetButtonRow = new LinearLayout(this);
        presetButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        presetButtonRow.setGravity(Gravity.CENTER);
        Button saveOpsPresetButton = button("Save ops preset");
        Button loadOpsPresetButton = button("Load ops preset");
        presetButtonRow.addView(saveOpsPresetButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        presetButtonRow.addView(loadOpsPresetButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        transportPanel.addView(presetButtonRow);
        root.addView(transportPanel);

        LinearLayout receivePanel = panel();
        receivePanel.addView(section("APRS / AFSK receive"));
        TextView receiveNote = label("Start or stop live microphone receive and view decoded APRS/AX.25 packets.", 13, MUTED, Typeface.NORMAL);
        receiveNote.setPadding(0, 0, 0, dp(8));
        receivePanel.addView(receiveNote);
        LinearLayout rxButtonRow = new LinearLayout(this);
        rxButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        rxButtonRow.setGravity(Gravity.CENTER);
        rxButton = button("Start live RX");
        Button stopRxButton = button("Stop RX");
        rxButtonRow.addView(rxButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rxButtonRow.addView(stopRxButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        receivePanel.addView(rxButtonRow);
        TextView receiveHint = label("Diagnostics show peak/RMS, clipping, signal/no sync, sync/waiting frame, FCS/checksum fail, and accepted packets in the compact status cards above.", 13, MUTED, Typeface.NORMAL);
        receiveHint.setPadding(0, dp(8), 0, 0);
        receivePanel.addView(receiveHint);
        root.addView(receivePanel);

        LinearLayout decoderPanel = panel();
        decoderPanel.addView(section("Offline decoder"));
        rawField = multiline("Paste FieldPacket text", "");
        limit(rawField, FieldPacketCodec.MAX_PACKET_CHARS);
        rawField.setMinLines(8);
        decoderPanel.addView(rawField);
        Button decodeButton = button("Decode pasted packet");
        decoderPanel.addView(decodeButton);
        root.addView(decoderPanel);

        LinearLayout resultPanel = panel();
        resultPanel.addView(section("Result"));
        resultView = label("Ready.", 14, TEXT, Typeface.NORMAL);
        resultView.setTypeface(Typeface.MONOSPACE);
        resultView.setTextIsSelectable(true);
        resultView.setBackgroundColor(PANEL_2);
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        resultPanel.addView(resultView);
        root.addView(resultPanel);

        setContentView(scroll);
        applyTopInsetPadding(root, dp(8));

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateEmergencyVisibility(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateEmergencyVisibility(); }
        });
        messageTemplateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateMessageTemplateDetails(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateMessageTemplateDetails(); }
        });
        radioPathSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateRadioPreset(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateRadioPreset(); }
        });
        transportSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateTransportProfile(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateTransportProfile(); }
        });
        txLevelSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateTxLevelLabel(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        composeButton.setOnClickListener(v -> composePacket());
        decodeButton.setOnClickListener(v -> decodePacket());
        sampleButton.setOnClickListener(v -> loadSample());
        loadTemplateButton.setOnClickListener(v -> loadSelectedMessageTemplate());
        saveTemplateButton.setOnClickListener(v -> saveCustomTemplate());
        loadSavedTemplateButton.setOnClickListener(v -> loadCustomTemplate());
        testPacketButton.setOnClickListener(v -> loadKnownGoodTestPacket());
        playToneButton.setOnClickListener(v -> playTestTone());
        stopToneButton.setOnClickListener(v -> stopTestTone(true));
        loadAprsButton.setOnClickListener(v -> loadAprsFromCurrentPacket());
        composeAprsButton.setOnClickListener(v -> composeAprsPacket());
        playAfskButton.setOnClickListener(v -> playAfskTransmit());
        stopAfskButton.setOnClickListener(v -> stopAfskTransmit(true));
        loadKissButton.setOnClickListener(v -> loadKissFromCurrentAprs());
        decodeKissButton.setOnClickListener(v -> decodeKissHex());
        pickPcmButton.setOnClickListener(v -> pickPcmImport());
        decodeGeneratedPcmButton.setOnClickListener(v -> decodeCurrentAfskAsImportedPcm());
        saveOpsPresetButton.setOnClickListener(v -> saveOpsPreset());
        loadOpsPresetButton.setOnClickListener(v -> loadOpsPreset());
        rxButton.setOnClickListener(v -> { if (receiving) stopReceive(true); else startReceiveIfPossible(); });
        stopRxButton.setOnClickListener(v -> stopReceive(true));

        updateEmergencyVisibility();
        updateMessageTemplateDetails();
        updateRadioPreset();
        updateTransportProfile();
        setRxListenStatus("RX: off");
        setRxLevelStatus("Signal: idle");
        setRxSyncStatus("Sync: no signal");
        setRxPacketStatus("Packet: none");
        composePacket();
        collapseAllSections(root);
    }

    @Override
    protected void onDestroy() {
        stopReceive(false);
        stopTestTone(false);
        stopAfskTransmit(false);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setRxPacketStatus("Packet: permission granted");
                startReceiveIfPossible();
            } else {
                setRxListenStatus("RX: off");
                setRxPacketStatus("Packet: mic permission denied");
                resultView.setText("Microphone permission denied. Compose/transmit/offline decode still work; live APRS/AFSK receive needs RECORD_AUDIO.");
                resultView.setTextColor(WARN);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PCM_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handlePcmImport(data.getData());
            } else {
                resultView.setText("PCM/WAV import cancelled. No file was read and no audio was stored.");
                resultView.setTextColor(MUTED);
            }
        }
    }

    private void composePacket() {
        FieldPacketMessage message = currentMessage();
        String raw = FieldPacketCodec.encode(message);
        rawField.setText(raw);
        FieldPacketCodec.DecodeResult decoded = FieldPacketCodec.decode(raw);
        if (decoded.ok) {
            resultView.setText("Encoded + verified locally.\nChecksum: " + decoded.checksum + "\n\n" + decoded.message.summary() + "\n\n" + raw);
            resultView.setTextColor(TEXT);
        } else {
            resultView.setText("Verify failed: " + decoded.error + "\n\n" + raw);
            resultView.setTextColor(WARN);
        }
    }

    private void decodePacket() {
        FieldPacketCodec.DecodeResult decoded = FieldPacketCodec.decode(rawField.getText().toString());
        if (decoded.ok) {
            FieldPacketMessage m = decoded.message;
            populateComposeFields(m);
            resultView.setText("Decoded OK.\nChecksum: " + decoded.checksum + "\n\n" + m.summary());
            resultView.setTextColor(TEXT);
        } else {
            resultView.setText("Decode failed.\n" + decoded.error);
            resultView.setTextColor(WARN);
        }
    }

    private void loadSample() {
        typeSpinner.setSelection(1);
        idField.setText(nextId());
        fromField.setText("FIELD");
        areaField.setText("LOCAL");
        expiresField.setText("2026-06-16T18:00Z");
        priorityField.setText("HIGH");
        locationField.setText("Grid/local landmark");
        needsField.setText("Water, battery, medical check");
        bodyField.setText("Emergency-format broadcast sample. Replace this with the actual message before transmit.");
        composePacket();
    }

    private void loadSelectedMessageTemplate() {
        MessageTemplate template = MessageTemplate.at(messageTemplateSpinner == null ? 0 : messageTemplateSpinner.getSelectedItemPosition());
        populateComposeFields(template.toMessage(nextId(), text(fromField)));
        composePacket();
        resultView.append("\n\nLoaded template: " + template.label + "\nReview before transmit.");
    }

    private void saveCustomTemplate() {
        SharedPreferences.Editor editor = prefs().edit();
        boolean emergency = typeSpinner.getSelectedItemPosition() == 1;
        editor.putBoolean(CUSTOM_TEMPLATE_PREFIX + "emergency", emergency);
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "from", text(fromField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "area", text(areaField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "expires", text(expiresField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "priority", text(priorityField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "location", text(locationField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "needs", text(needsField));
        editor.putString(CUSTOM_TEMPLATE_PREFIX + "body", text(bodyField));
        editor.apply();
        resultView.setText("Custom template saved.\n"
                + "Packet ID is regenerated when the template is loaded.\n"
                + "Avoid saving one-time or sensitive traffic as a template.");
        resultView.setTextColor(TEXT);
    }

    private void loadCustomTemplate() {
        SharedPreferences p = prefs();
        if (!p.contains(CUSTOM_TEMPLATE_PREFIX + "body")) {
            resultView.setText("No custom template saved yet. Load/edit a built-in template or compose fields, then tap Save custom.");
            resultView.setTextColor(MUTED);
            return;
        }
        boolean emergency = p.getBoolean(CUSTOM_TEMPLATE_PREFIX + "emergency", false);
        typeSpinner.setSelection(emergency ? 1 : 0);
        idField.setText(nextId());
        fromField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "from", "FIELD"));
        areaField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "area", "LOCAL"));
        expiresField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "expires", ""));
        priorityField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "priority", "HIGH"));
        locationField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "location", ""));
        needsField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "needs", ""));
        bodyField.setText(p.getString(CUSTOM_TEMPLATE_PREFIX + "body", ""));
        updateEmergencyVisibility();
        composePacket();
        resultView.append("\n\nLoaded saved custom template. Review before transmit.");
    }

    private void updateMessageTemplateDetails() {
        if (messageTemplateDetailsView == null) return;
        MessageTemplate template = MessageTemplate.at(messageTemplateSpinner == null ? 0 : messageTemplateSpinner.getSelectedItemPosition());
        messageTemplateDetailsView.setText(template.details());
    }

    private void loadKnownGoodTestPacket() {
        String packet = FieldPacketSamples.knownGoodCalibrationPacket();
        rawField.setText(packet);
        FieldPacketCodec.DecodeResult decoded = FieldPacketCodec.decode(packet);
        if (decoded.ok) {
            populateComposeFields(decoded.message);
            resultView.setText("Known-good calibration packet generated and verified locally.\nChecksum: "
                    + decoded.checksum + "\n\nUse this stable FP1 text for radio-path checks before live traffic.\n\n" + packet);
            resultView.setTextColor(TEXT);
        } else {
            resultView.setText("Known-good packet generation failed local verification: " + decoded.error + "\n\n" + packet);
            resultView.setTextColor(WARN);
        }
    }

    private void loadAprsFromCurrentPacket() {
        FieldPacketMessage message = currentMessage();
        aprsInfoField.setText(AprsTransmitPacket.infoFromFieldPacket(message));
        composeAprsPacket();
    }

    private void composeAprsPacket() {
        AprsTransmitPacket packet = currentAprsPacket();
        syncAprsFields(packet);
        Ax25Frame frame = Ax25Frame.fromAprs(packet);
        resultView.setText("APRS/AX.25 packet composed locally. No audio started.\n\n"
                + "TNC2:\n" + packet.tnc2Line()
                + "\n\n" + frame.summary()
                + "\nFrame hex with FCS:\n" + frame.frameHex(96)
                + "\n\nTransmit uses Bell 202 AFSK 1200 from memory only; no audio file is written.");
        resultView.setTextColor(TEXT);
    }

    private void loadKissFromCurrentAprs() {
        AprsTransmitPacket packet = currentAprsPacket();
        syncAprsFields(packet);
        Ax25Frame frame = Ax25Frame.fromAprs(packet);
        KissFrame kiss = KissFrame.fromAx25(frame, 0);
        byte[] bytes = kiss.toBytes();
        kissHexField.setText(KissFrame.toHex(bytes));
        resultView.setText("KISS data frame composed from current AX.25 UI packet.\n\n"
                + "TNC2:\n" + packet.tnc2Line()
                + "\n\n" + kiss.summary()
                + "\n\nKISS hex:\n" + KissFrame.toHex(bytes)
                + "\n\nNote: KISS payload carries AX.25 without HDLC flags/FCS; the TNC/radio path handles the over-the-air FCS.");
        resultView.setTextColor(TEXT);
    }

    private void decodeKissHex() {
        try {
            byte[] bytes = KissFrame.parseHex(text(kissHexField));
            if (bytes.length == 0) {
                resultView.setText("Paste KISS frame hex first, or tap Load current AX.25 as KISS.");
                resultView.setTextColor(MUTED);
                return;
            }
            List<KissFrame> frames = KissFrame.decodeStream(bytes);
            if (frames.isEmpty()) {
                Ax25Frame.ParsedUiFrame raw = Ax25Frame.parseUiFrameWithoutFcs(bytes);
                resultView.setText("No KISS FEND-delimited frame found, but the bytes parse as a raw no-FCS AX.25 UI payload.\n\n"
                        + raw.summary()
                        + "\nTNC2:\n" + raw.tnc2Line()
                        + "\n\nPasted bytes decoded in memory.");
                resultView.setTextColor(TEXT);
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Decoded ").append(frames.size()).append(" KISS frame(s) from pasted bytes.\n");
            for (int i = 0; i < frames.size(); i++) {
                KissFrame frame = frames.get(i);
                sb.append("\nFrame ").append(i + 1).append(": ").append(frame.summary());
                if (frame.isDataFrame()) {
                    try {
                        Ax25Frame.ParsedUiFrame parsed = frame.parseAx25UiFrame();
                        sb.append("\nTNC2:\n").append(parsed.tnc2Line());
                    } catch (IllegalArgumentException ex) {
                        sb.append("\nAX.25 parse failed: ").append(ex.getMessage());
                    }
                }
                sb.append("\nPayload hex:\n").append(KissFrame.toHex(frame.payload)).append('\n');
            }
            resultView.setText(sb.toString());
            resultView.setTextColor(TEXT);
        } catch (Exception ex) {
            resultView.setText("KISS decode failed: " + ex.getMessage());
            resultView.setTextColor(WARN);
        }
    }

    private void pickPcmImport() {
        stopReceive(false);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_PCM_IMPORT);
            resultView.setText("Choose a WAV PCM file or raw signed 16-bit little-endian PCM file. FieldPacket will read it once into memory for decode and will not store a copy.");
            resultView.setTextColor(MUTED);
        } catch (Exception ex) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            startActivityForResult(fallback, REQ_PCM_IMPORT);
        }
    }

    private void handlePcmImport(Uri uri) {
        try {
            stopReceive(false);
            byte[] bytes = readUriBytes(uri, MAX_IMPORT_BYTES);
            int rawRate = parseInt(rawSampleRateField, Bell202AfskDecoder.SAMPLE_RATE_HZ);
            Pcm16Import.ImportedPcm imported = Pcm16Import.fromWavOrRaw(bytes, rawRate);
            Bell202AfskDecoder.DecodeReport report = Bell202AfskDecoder.analyze(imported.samples);
            reportImportedPcmDecode("PCM/WAV import decoded from selected file", imported, report);
        } catch (Exception ex) {
            setRxListenStatus("RX: import error");
            setRxPacketStatus("Packet: import failed");
            resultView.setText("PCM/WAV import failed: " + ex.getMessage()
                    + "\n\nNo audio was stored. For raw files, use signed 16-bit little-endian mono and set the sample rate field first.");
            resultView.setTextColor(WARN);
        }
    }

    private void decodeCurrentAfskAsImportedPcm() {
        try {
            AprsTransmitPacket packet = currentAprsPacket();
            syncAprsFields(packet);
            RadioTestPlan plan = RadioTestPlan.create(RadioPathPreset.at(0), 40, 40, 1, 70);
            Bell202AfskModulator.AfskTransmission tx = Bell202AfskModulator.generate(packet, plan);
            Pcm16Import.ImportedPcm imported = Pcm16Import.fromRawLittleEndian(shortsToLittleEndian(tx.pcm), Bell202AfskModulator.SAMPLE_RATE_HZ);
            Bell202AfskDecoder.DecodeReport report = Bell202AfskDecoder.analyze(imported.samples);
            reportImportedPcmDecode("Generated current APRS AFSK as manual PCM import", imported, report);
        } catch (Exception ex) {
            resultView.setText("Generated PCM import decode failed: " + ex.getMessage());
            resultView.setTextColor(WARN);
        }
    }

    private void reportImportedPcmDecode(String title, Pcm16Import.ImportedPcm imported, Bell202AfskDecoder.DecodeReport report) {
        setRxListenStatus("RX: import");
        setRxLevelStatus("Signal: " + report.metrics.shortStatus());
        setRxSyncStatus("Sync: " + report.shortStatus());
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n").append(imported.summary()).append("\n").append(report.diagnosticLine());
        if (report.decodedAccepted && report.result != null) {
            Ax25Frame.ParsedUiFrame frame = report.result.frame;
            setRxPacketStatus("Packet: imported " + frame.source + ">" + frame.destination);
            sb.append("\n\nDecoded APRS/AX.25 packet accepted.\nTNC2:\n")
                    .append(frame.tnc2Line())
                    .append("\n\nAPRS information:\n")
                    .append(frame.information)
                    .append("\n\n")
                    .append(frame.summary())
                    .append("\nRaw AX.25 frame with FCS:\n")
                    .append(frame.frameHex(160));
            resultView.setTextColor(TEXT);
        } else {
            if (report.fcsFailed) setRxPacketStatus("Packet: import FCS/checksum fail");
            else if (report.syncSeen) setRxPacketStatus("Packet: import sync only");
            else if (report.signalPresent) setRxPacketStatus("Packet: import no sync");
            else setRxPacketStatus("Packet: import no signal");
            sb.append("\n\nNo accepted packet decoded from this import. Try a cleaner SDR demodulated PCM source, check sample rate, and avoid clipping.");
            resultView.setTextColor(WARN);
        }
        sb.append("\n\nImport data was processed in memory only. FieldPacket did not store audio or start a background listener.");
        resultView.setText(sb.toString());
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("could not open selected file");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("selected file exceeds " + maxBytes + " byte in-memory import limit");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private byte[] shortsToLittleEndian(short[] samples) {
        byte[] bytes = new byte[(samples == null ? 0 : samples.length) * 2];
        if (samples == null) return bytes;
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private void playAfskTransmit() {
        RadioTestPlan plan = currentRadioPlan();
        syncRadioControls(plan);
        if (!plan.preset.transmitCapable) {
            resultView.setText("No APRS AFSK audio generated.\n" + plan.preset.details()
                    + "\n\nThis preset is receive-only planning; it does not request microphone, USB, Bluetooth, or network permissions.");
            resultView.setTextColor(WARN);
            return;
        }

        AprsTransmitPacket packet = currentAprsPacket();
        syncAprsFields(packet);
        Bell202AfskModulator.AfskTransmission tx = Bell202AfskModulator.generate(packet, plan);
        if (tx.pcm.length == 0) {
            resultView.setText("AFSK generation produced no samples.");
            resultView.setTextColor(WARN);
            return;
        }

        stopTestTone(false);
        stopAfskTransmit(false);
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Bell202AfskModulator.SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(tx.pcm.length * 2)
                    .build();
            int written = track.write(tx.pcm, 0, tx.pcm.length);
            if (written != tx.pcm.length) {
                track.release();
                resultView.setText("AudioTrack accepted " + written + " of " + tx.pcm.length + " samples; AFSK transmit not started.");
                resultView.setTextColor(WARN);
                return;
            }
            track.setNotificationMarkerPosition(tx.pcm.length);
            final AudioTrack finalTrack = track;
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack audioTrack) {
                    runOnUiThread(() -> {
                        if (afskTrack == finalTrack) {
                            afskTrack = null;
                            resultView.append("\n\nAFSK playback finished; in-memory buffer released.");
                        }
                        finalTrack.release();
                    });
                }
                @Override public void onPeriodicNotification(AudioTrack audioTrack) {}
            });
            afskTrack = track;
            track.play();
            resultView.setText("Generated and started in-memory APRS/AX.25 Bell 202 AFSK transmit audio. No audio file was written.\n"
                    + tx.summary()
                    + "\n" + tx.frame.summary()
                    + "\n\nTNC2:\n" + packet.tnc2Line()
                    + "\n\n" + plan.preset.summary);
            resultView.setTextColor(TEXT);
        } catch (RuntimeException ex) {
            if (track != null) {
                track.release();
            }
            afskTrack = null;
            resultView.setText("Could not start AudioTrack AFSK transmit: " + ex.getMessage());
            resultView.setTextColor(WARN);
        }
    }

    private void stopAfskTransmit(boolean announce) {
        AudioTrack track = afskTrack;
        if (track == null) {
            if (announce && resultView != null) {
                resultView.setText("No AFSK transmit audio is currently playing.");
                resultView.setTextColor(MUTED);
            }
            return;
        }
        afskTrack = null;
        try {
            track.stop();
        } catch (RuntimeException ignored) {
            // Track may already be stopped or not fully initialized.
        }
        track.release();
        if (announce && resultView != null) {
            resultView.setText("AFSK transmit stopped; in-memory buffer released.");
            resultView.setTextColor(TEXT);
        }
    }

    private void startReceiveIfPossible() {
        if (receiving) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setRxListenStatus("RX: permission needed");
            setRxPacketStatus("Packet: tap allow mic for live RX");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            resultView.setText("Live APRS/AFSK receive needs RECORD_AUDIO. Audio is processed in memory only; no Internet permission and no stored audio.");
            resultView.setTextColor(MUTED);
            return;
        }
        stopAfskTransmit(false);
        stopTestTone(false);
        receiving = true;
        rxRing.clear();
        updateRxButtonText();
        setRxListenStatus("RX: starting");
        setRxLevelStatus("Signal: waiting");
        setRxSyncStatus("Sync: waiting");
        setRxPacketStatus("Packet: listening");
        rxThread = new Thread(this::receiveLoop, "fieldpacket-aprs-rx");
        rxThread.start();
        resultView.setText("Live APRS/AFSK receive started. Hold the radio/phone audio near the microphone or use a safe audio interface. Audio stays in RAM and is not stored.");
        resultView.setTextColor(TEXT);
    }

    private void stopReceive(boolean announce) {
        boolean wasReceiving = receiving;
        receiving = false;
        Thread thread = rxThread;
        if (thread != null) thread.interrupt();
        rxThread = null;
        rxRing.clear();
        updateRxButtonText();
        setRxListenStatus("RX: off");
        setRxLevelStatus("Signal: idle");
        setRxSyncStatus("Sync: no signal");
        if (announce && resultView != null) {
            if (wasReceiving) {
                setRxPacketStatus("Packet: stopped");
                resultView.setText("Live APRS/AFSK receive stopped. Microphone is off; in-memory audio buffer cleared.");
                resultView.setTextColor(TEXT);
            } else {
                setRxPacketStatus("Packet: idle");
                resultView.setText("Live APRS/AFSK receive is not running.");
                resultView.setTextColor(MUTED);
            }
        }
    }

    private void receiveLoop() {
        int min = AudioRecord.getMinBufferSize(Bell202AfskDecoder.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            runOnUiThread(() -> {
                receiving = false;
                updateRxButtonText();
                setRxListenStatus("RX: unavailable");
                setRxPacketStatus("Packet: AudioRecord buffer failed");
                resultView.setText("Microphone unavailable: AudioRecord could not allocate a receive buffer.");
                resultView.setTextColor(WARN);
            });
            return;
        }
        int bufferBytes = Math.max(min * 4, 4096);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                Bell202AfskDecoder.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes);
        short[] buffer = new short[Math.max(1024, bufferBytes / 4)];
        long lastLevel = 0;
        long lastDecode = 0;
        try {
            recorder.startRecording();
            runOnUiThread(() -> {
                setRxListenStatus("RX: live");
                setRxPacketStatus("Packet: listening");
                resultView.setText("Microphone live for APRS/AFSK receive @ " + Bell202AfskDecoder.SAMPLE_RATE_HZ
                        + " Hz. Buffer " + bufferBytes + " bytes. Audio is in memory only.");
                resultView.setTextColor(TEXT);
            });
            while (receiving && !Thread.currentThread().isInterrupted()) {
                int n = recorder.read(buffer, 0, buffer.length);
                if (n <= 0) continue;
                rxRing.append(buffer, n);
                long now = System.currentTimeMillis();
                if (now - lastLevel >= 500) {
                    lastLevel = now;
                    Bell202AfskDecoder.SignalMetrics metrics = Bell202AfskDecoder.analyzeSignal(buffer, n);
                    runOnUiThread(() -> setRxLevelStatus("Signal: " + metrics.shortStatus()));
                }
                if (now - lastDecode >= 850) {
                    lastDecode = now;
                    short[] snapshot = rxRing.snapshot();
                    Bell202AfskDecoder.DecodeReport report = Bell202AfskDecoder.analyze(snapshot);
                    runOnUiThread(() -> handleReceiveReport(report));
                    if (report.decodedAccepted) rxRing.clear();
                }
            }
        } catch (Exception ex) {
            runOnUiThread(() -> {
                receiving = false;
                updateRxButtonText();
                setRxListenStatus("RX: error");
                setRxPacketStatus("Packet: listen error");
                resultView.setText("Live receive error: " + ex.getMessage());
                resultView.setTextColor(WARN);
            });
        } finally {
            try {
                recorder.stop();
            } catch (Exception ignored) {
                // Recorder may not have reached recording state.
            }
            recorder.release();
        }
    }

    private void handleReceiveReport(Bell202AfskDecoder.DecodeReport report) {
        if (report == null) return;
        setRxLevelStatus("Signal: " + report.metrics.shortStatus());
        setRxSyncStatus("Sync: " + report.shortStatus());
        if (report.decodedAccepted && report.result != null) {
            Ax25Frame.ParsedUiFrame frame = report.result.frame;
            setRxPacketStatus("Packet: accepted " + frame.source + ">" + frame.destination);
            resultView.setText("RX decoded APRS/AX.25 packet accepted.\n"
                    + report.diagnosticLine()
                    + "\n\nTNC2:\n" + frame.tnc2Line()
                    + "\n\nAPRS information:\n" + frame.information
                    + "\n\n" + frame.summary()
                    + "\nRaw AX.25 frame with FCS:\n" + frame.frameHex(160)
                    + "\n\nReceive audio was processed in memory only and not stored.");
            resultView.setTextColor(TEXT);
        } else if (report.fcsFailed) {
            setRxPacketStatus("Packet: FCS/checksum fail");
        } else if (report.syncSeen) {
            setRxPacketStatus("Packet: sync seen, waiting frame");
        } else if (report.signalPresent) {
            setRxPacketStatus("Packet: signal present, no sync");
        } else {
            setRxPacketStatus("Packet: no signal");
        }
    }

    private void updateRxButtonText() {
        if (rxButton != null) runOnUiThread(() -> rxButton.setText(receiving ? "Stop live RX" : "Start live RX"));
    }

    private AprsTransmitPacket currentAprsPacket() {
        return AprsTransmitPacket.create(text(aprsSourceField), text(aprsDestinationField), text(aprsPathField), text(aprsInfoField));
    }

    private void syncAprsFields(AprsTransmitPacket packet) {
        if (aprsSourceField != null) aprsSourceField.setText(packet.source);
        if (aprsDestinationField != null) aprsDestinationField.setText(packet.destination);
        if (aprsPathField != null) aprsPathField.setText(packet.path);
        if (aprsInfoField != null) aprsInfoField.setText(packet.information);
    }

    private void playTestTone() {
        RadioTestPlan plan = currentRadioPlan();
        syncRadioControls(plan);
        if (!plan.preset.transmitCapable) {
            resultView.setText("No transmit tone generated.\n" + plan.preset.details()
                    + "\n\nThis path is receive-only; choose a transmit-capable radio path to play a tone.");
            resultView.setTextColor(WARN);
            return;
        }

        short[] pcm = InMemoryToneGenerator.generateTestTone(plan);
        if (pcm.length == 0) {
            resultView.setText("Test tone generation produced no samples.");
            resultView.setTextColor(WARN);
            return;
        }

        stopAfskTransmit(false);
        stopTestTone(false);
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(InMemoryToneGenerator.SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            int written = track.write(pcm, 0, pcm.length);
            if (written != pcm.length) {
                track.release();
                resultView.setText("AudioTrack accepted " + written + " of " + pcm.length + " samples; tone not started.");
                resultView.setTextColor(WARN);
                return;
            }
            track.setNotificationMarkerPosition(pcm.length);
            final AudioTrack finalTrack = track;
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack audioTrack) {
                    runOnUiThread(() -> {
                        if (toneTrack == finalTrack) {
                            toneTrack = null;
                            resultView.append("\n\nTone playback finished; in-memory buffer released.");
                        }
                        finalTrack.release();
                    });
                }
                @Override public void onPeriodicNotification(AudioTrack audioTrack) {}
            });
            toneTrack = track;
            track.play();
            resultView.setText("Generated and started in-memory PCM test tone. No audio file was written.\n"
                    + plan.summary()
                    + "\nSamples: " + pcm.length
                    + " @ " + InMemoryToneGenerator.SAMPLE_RATE_HZ + " Hz"
                    + "\nPeak sample: " + InMemoryToneGenerator.maxAbsSample(pcm)
                    + "\n\n" + plan.preset.summary);
            resultView.setTextColor(TEXT);
        } catch (RuntimeException ex) {
            if (track != null) {
                track.release();
            }
            toneTrack = null;
            resultView.setText("Could not start AudioTrack test tone: " + ex.getMessage());
            resultView.setTextColor(WARN);
        }
    }

    private void stopTestTone(boolean announce) {
        AudioTrack track = toneTrack;
        if (track == null) {
            if (announce && resultView != null) {
                resultView.setText("No test tone is currently playing.");
                resultView.setTextColor(MUTED);
            }
            return;
        }
        toneTrack = null;
        try {
            track.stop();
        } catch (RuntimeException ignored) {
            // Track may already be stopped or not fully initialized.
        }
        track.release();
        if (announce && resultView != null) {
            resultView.setText("Test tone stopped; in-memory buffer released.");
            resultView.setTextColor(TEXT);
        }
    }

    private RadioTestPlan currentRadioPlan() {
        RadioPathPreset preset = RadioPathPreset.at(radioPathSpinner == null ? 0 : radioPathSpinner.getSelectedItemPosition());
        int leadIn = parseInt(leadInField, preset.defaultLeadInMs);
        int tail = parseInt(tailField, preset.defaultTailMs);
        int repeats = parseInt(repeatField, preset.defaultRepeats);
        int level = txLevelSeek == null ? preset.defaultLevelPercent : txLevelSeek.getProgress();
        return RadioTestPlan.create(preset, leadIn, tail, repeats, level);
    }

    private void updateRadioPreset() {
        RadioPathPreset preset = RadioPathPreset.at(radioPathSpinner == null ? 0 : radioPathSpinner.getSelectedItemPosition());
        if (radioPathDetailsView != null) {
            radioPathDetailsView.setText(preset.details());
        }
        syncRadioControls(RadioTestPlan.fromPreset(preset));
    }

    private void updateTransportProfile() {
        TransportProfile profile = TransportProfile.at(transportSpinner == null ? 0 : transportSpinner.getSelectedItemPosition());
        if (transportDetailsView != null) {
            transportDetailsView.setText(profile.details());
        }
    }

    private void saveOpsPreset() {
        SharedPreferences.Editor editor = prefs().edit();
        editor.putInt(OPS_PRESET_PREFIX + "radio_index", radioPathSpinner == null ? 0 : radioPathSpinner.getSelectedItemPosition());
        editor.putString(OPS_PRESET_PREFIX + "lead_in", text(leadInField));
        editor.putString(OPS_PRESET_PREFIX + "tail", text(tailField));
        editor.putString(OPS_PRESET_PREFIX + "repeat", text(repeatField));
        editor.putInt(OPS_PRESET_PREFIX + "level", txLevelSeek == null ? 0 : txLevelSeek.getProgress());
        editor.putInt(OPS_PRESET_PREFIX + "transport_index", transportSpinner == null ? 0 : transportSpinner.getSelectedItemPosition());
        editor.putString(OPS_PRESET_PREFIX + "aprs_source", text(aprsSourceField));
        editor.putString(OPS_PRESET_PREFIX + "aprs_destination", text(aprsDestinationField));
        editor.putString(OPS_PRESET_PREFIX + "aprs_path", text(aprsPathField));
        editor.putString(OPS_PRESET_PREFIX + "raw_sample_rate", text(rawSampleRateField));
        editor.apply();
        resultView.setText("Ops preset saved.\n"
                + "Included: radio path, lead-in/tail/repeats/level, transport profile, APRS source/destination/path, and raw PCM sample rate.");
        resultView.setTextColor(TEXT);
    }

    private void loadOpsPreset() {
        SharedPreferences p = prefs();
        if (!p.contains(OPS_PRESET_PREFIX + "radio_index")) {
            resultView.setText("No ops preset saved yet. Adjust radio/transport/APRS controls, then tap Save ops preset.");
            resultView.setTextColor(MUTED);
            return;
        }
        if (radioPathSpinner != null) radioPathSpinner.setSelection(p.getInt(OPS_PRESET_PREFIX + "radio_index", 0));
        if (leadInField != null) leadInField.setText(p.getString(OPS_PRESET_PREFIX + "lead_in", text(leadInField)));
        if (tailField != null) tailField.setText(p.getString(OPS_PRESET_PREFIX + "tail", text(tailField)));
        if (repeatField != null) repeatField.setText(p.getString(OPS_PRESET_PREFIX + "repeat", text(repeatField)));
        if (txLevelSeek != null) txLevelSeek.setProgress(p.getInt(OPS_PRESET_PREFIX + "level", txLevelSeek.getProgress()));
        if (transportSpinner != null) transportSpinner.setSelection(p.getInt(OPS_PRESET_PREFIX + "transport_index", 0));
        if (aprsSourceField != null) aprsSourceField.setText(p.getString(OPS_PRESET_PREFIX + "aprs_source", text(aprsSourceField)));
        if (aprsDestinationField != null) aprsDestinationField.setText(p.getString(OPS_PRESET_PREFIX + "aprs_destination", text(aprsDestinationField)));
        if (aprsPathField != null) aprsPathField.setText(p.getString(OPS_PRESET_PREFIX + "aprs_path", text(aprsPathField)));
        if (rawSampleRateField != null) rawSampleRateField.setText(p.getString(OPS_PRESET_PREFIX + "raw_sample_rate", text(rawSampleRateField)));
        updateTxLevelLabel();
        updateTransportProfile();
        resultView.setText("Ops preset loaded.\n"
                + "Review the current message body and APRS information before transmit.");
        resultView.setTextColor(TEXT);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void syncRadioControls(RadioTestPlan plan) {
        if (leadInField != null) leadInField.setText(String.valueOf(plan.leadInMs));
        if (tailField != null) tailField.setText(String.valueOf(plan.tailMs));
        if (repeatField != null) repeatField.setText(String.valueOf(plan.repeats));
        if (txLevelSeek != null) txLevelSeek.setProgress(plan.levelPercent);
        updateTxLevelLabel();
    }

    private void updateTxLevelLabel() {
        if (txLevelLabel != null && txLevelSeek != null) {
            txLevelLabel.setText("TX level / gain: " + txLevelSeek.getProgress() + "%");
        }
    }

    private void populateComposeFields(FieldPacketMessage m) {
        typeSpinner.setSelection(m.type == FieldPacketMessage.Type.EMERGENCY ? 1 : 0);
        idField.setText(m.id);
        fromField.setText(m.from);
        areaField.setText(m.area);
        expiresField.setText(m.expires);
        priorityField.setText(m.priority);
        locationField.setText(m.location);
        needsField.setText(m.needs);
        bodyField.setText(m.body);
    }

    private FieldPacketMessage currentMessage() {
        boolean emergency = typeSpinner.getSelectedItemPosition() == 1;
        if (emergency) {
            return FieldPacketMessage.emergency(text(idField), text(fromField), text(areaField), text(expiresField),
                    text(priorityField), text(locationField), text(needsField), text(bodyField));
        }
        return FieldPacketMessage.bulletin(text(idField), text(fromField), text(areaField), text(expiresField), text(bodyField));
    }

    private void updateEmergencyVisibility() {
        if (emergencyFields != null && typeSpinner != null) {
            emergencyFields.setVisibility(typeSpinner.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);
        }
    }

    private String nextId() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "FP-" + format.format(new Date());
    }

    private static String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private static int parseInt(EditText editText, int fallback) {
        try {
            String value = text(editText).trim();
            if (value.isEmpty()) return fallback;
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void buildRxStatusCards(LinearLayout root) {
        rxListenCard = statusCard("RX: off");
        rxLevelCard = statusCard("Signal: idle");
        rxSyncCard = statusCard("Sync: no signal");
        rxPacketCard = statusCard("Packet: none");

        LinearLayout rowA = row();
        rowA.addView(rxListenCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rowA.addView(rxLevelCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(rowA);

        LinearLayout rowB = row();
        rowB.addView(rxSyncCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rowB.addView(rxPacketCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(rowB);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private TextView statusCard(String text) {
        TextView view = label(text, 11, TEXT, Typeface.BOLD);
        view.setSingleLine(true);
        view.setPadding(dp(7), dp(5), dp(7), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL_2);
        bg.setStroke(dp(1), ACCENT);
        bg.setCornerRadius(dp(10));
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, dp(6), dp(4));
        view.setLayoutParams(params);
        return view;
    }

    private void setRxListenStatus(String text) { if (rxListenCard != null) rxListenCard.setText(text); }
    private void setRxLevelStatus(String text) { if (rxLevelCard != null) rxLevelCard.setText(text); }
    private void setRxSyncStatus(String text) { if (rxSyncCard != null) rxSyncCard.setText(text); }
    private void setRxPacketStatus(String text) { if (rxPacketCard != null) rxPacketCard.setText(text); }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setStroke(dp(1), Color.rgb(45, 64, 74));
        bg.setCornerRadius(dp(14));
        layout.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        layout.setLayoutParams(params);
        return layout;
    }

    private TextView section(String text) {
        TextView view = label(text + "  ▾", 15, TEXT, Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(8));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOnClickListener(v -> {
            if (!(v.getParent() instanceof LinearLayout) || !(v instanceof TextView)) return;
            LinearLayout parent = (LinearLayout) v.getParent();
            boolean expanded = false;
            for (int i = 1; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i).getVisibility() == View.VISIBLE) {
                    expanded = true;
                    break;
                }
            }
            boolean expand = !expanded;
            setSectionExpanded(parent, (TextView) v, text, expand);
            if (expand) updateEmergencyVisibility();
        });
        return view;
    }

    private void setSectionExpanded(LinearLayout parent, TextView header, String text, boolean expanded) {
        for (int i = 1; i < parent.getChildCount(); i++) {
            parent.getChildAt(i).setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        header.setText(text + (expanded ? "  ▾" : "  ▸"));
    }

    private void collapseAllSections(LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout panel = (LinearLayout) child;
            if (panel.getChildCount() == 0 || !(panel.getChildAt(0) instanceof TextView)) continue;
            TextView header = (TextView) panel.getChildAt(0);
            String title = sectionTitle(header.getText().toString());
            if (title == null) continue;
            setSectionExpanded(panel, header, title, false);
        }
    }

    private String sectionTitle(String headerText) {
        if (headerText == null) return null;
        if (headerText.endsWith("  ▾") || headerText.endsWith("  ▸")) {
            return headerText.substring(0, headerText.length() - 3);
        }
        return null;
    }

    private void applyTopInsetPadding(View v, int baseTopPadding) {
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusTop;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets status = insets.getInsets(android.view.WindowInsets.Type.statusBars());
                statusTop = status.top;
            } else {
                statusTop = insets.getSystemWindowInsetTop();
            }
            view.setPadding(view.getPaddingLeft(), baseTopPadding + statusTop, view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
        v.requestApplyInsets();
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEXT_FIELD_CHARS)});
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL_2);
        bg.setStroke(dp(1), Color.rgb(45, 64, 74));
        bg.setCornerRadius(dp(3));
        edit.setBackground(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) edit.setBackgroundTintList(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        edit.setLayoutParams(params);
        return edit;
    }

    private EditText numberInput(String hint, String value) {
        EditText edit = input(hint, value);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        return edit;
    }

    private EditText multiline(String hint, String value) {
        EditText edit = input(hint, value);
        edit.setSingleLine(false);
        edit.setMinLines(4);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setTypeface(Typeface.MONOSPACE);
        limit(edit, MAX_MESSAGE_BODY_CHARS);
        return edit;
    }

    private static void limit(EditText edit, int maxChars) {
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxChars)});
    }

    private <T> ArrayAdapter<T> spinnerAdapter(T[] values) {
        return new ArrayAdapter<T>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerView(view, false);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerView(view, true);
                return view;
            }
        };
    }

    private void styleSpinner(Spinner spinner) {
        spinner.setBackgroundColor(PANEL_2);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            spinner.setBackgroundTintList(ColorStateList.valueOf(PANEL_2));
        }
    }

    private void styleSpinnerView(View view, boolean dropdown) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(TEXT);
            tv.setHintTextColor(MUTED);
            tv.setTextSize(14);
            tv.setPadding(dp(8), dropdown ? dp(10) : dp(6), dp(8), dropdown ? dp(10) : dp(6));
        }
        view.setBackgroundColor(dropdown ? PANEL_2 : BG);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(TEXT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setBackgroundTintList(ColorStateList.valueOf(PANEL_2));
        } else {
            button.setBackgroundColor(PANEL_2);
        }
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class RingSamples {
        private final short[] data;
        private int write;
        private boolean full;

        RingSamples(int capacity) {
            data = new short[Math.max(1, capacity)];
        }

        synchronized void append(short[] src, int n) {
            int limit = Math.min(n, src == null ? 0 : src.length);
            for (int i = 0; i < limit; i++) {
                data[write++] = src[i];
                if (write >= data.length) {
                    write = 0;
                    full = true;
                }
            }
        }

        synchronized short[] snapshot() {
            int size = full ? data.length : write;
            short[] out = new short[size];
            if (!full) {
                System.arraycopy(data, 0, out, 0, size);
                return out;
            }
            int tail = data.length - write;
            System.arraycopy(data, write, out, 0, tail);
            System.arraycopy(data, 0, out, tail, write);
            return out;
        }

        synchronized void clear() {
            write = 0;
            full = false;
        }
    }
}
