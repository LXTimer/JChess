package com.jchess.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.InputStream;

public class SoundManager {
    public static void playMove() {
        playSound("/com/jchess/resources/sounds/move.wav");
    }

    public static void playCapture() {
        playSound("/com/jchess/resources/sounds/capture.wav");
    }

    public static void playBeep() {
        playSound("/com/jchess/resources/sounds/beep.wav");
    }

    private static void playSound(String soundFile) {
        try {
            InputStream is = SoundManager.class.getResourceAsStream(soundFile);
            if (is == null) {
                System.err.println("Sound file not found: " + soundFile);
                return;
            }
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(is));
            
            // Set volume to a reasonable level
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(-10.0f);
            
            clip.start();
        } catch (Exception e) {
            System.err.println("Failed to play sound.");
        }
    }
}