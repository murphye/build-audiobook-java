// @@@SNIPSTART audiobook-project-java-tts-implementation
package ttsworker;

import io.temporal.failure.ApplicationFailure;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;

public class TTSActivitiesImpl implements TTSActivities {
    private static final Logger logger = LoggerFactory.getLogger(TTSActivitiesImpl.class);

    private final OpenAiAudioSpeechModel speechModel;

    public TTSActivitiesImpl(OpenAiAudioSpeechModel speechModel) {
        this.speechModel = speechModel;
    }

    ApplicationFailure fail(String reason, String issue) {
        return ApplicationFailure.newFailure(reason, issue);
    }

    @Override
    public List<String> readFile(String inputPath) {
        Path canonicalPath;
        
        try {

            if (inputPath == null || inputPath.isEmpty() || !inputPath.endsWith(".txt")) {
                throw fail("Invalid path", "MALFORMED_INPUT");
            }

            if (inputPath.startsWith("~")) {
                String home = System.getProperty("user.home");
                inputPath = home + inputPath.substring(1);
            }

            canonicalPath = Paths.get(inputPath)
                .toAbsolutePath().normalize()
                .toRealPath(LinkOption.NOFOLLOW_LINKS);

            if (!Files.exists(canonicalPath) ||
                !Files.isReadable(canonicalPath) ||
                Files.size(canonicalPath) == 0) {
                throw fail("Invalid path", "MALFORMED_INPUT");
            }

        } catch (InvalidPathException | IOException e) {
            throw fail("Invalid path", "MALFORMED_INPUT");
        }

        String content;
        try {
            content = Files.readString(canonicalPath).trim();
        } catch (IOException e) {
            throw fail("Invalid content", "MISSING_CONTENT");
        }

        int MAX_TOKENS = 512;
        float AVERAGE_TOKENS_PER_WORD = 1.33f;

        List<String> chunks = new ArrayList<>();
        String[] words = content.split("\\s+");
        StringJoiner chunk = new StringJoiner(" ");

        for (String word : words) {
            if ((chunk.length() + word.length()) * AVERAGE_TOKENS_PER_WORD <= MAX_TOKENS) {
                chunk.add(word);
            } else {
                chunks.add(chunk.toString());
                chunk = new StringJoiner(" ");
                chunk.add(word);
            }
        }

        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }

        return chunks;
    }

    @Override
    public Path createTemporaryFile() {
        try {
            Path tempFile = Files.createTempFile(null, null);
            return tempFile;
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            fail("Unable to create temporary work file", "FILE_ERROR");
        }
        return null;
    }

    byte[] textToSpeech(String text) {
        SpeechPrompt speechPrompt = new SpeechPrompt(text);
        SpeechResponse response = speechModel.call(speechPrompt);
        return response.getResult().getOutput();
    }

    @Override
    public void process(String chunk, Path outputPath) {
        byte[] audio = textToSpeech(chunk);

        try {
            Files.write(outputPath, audio,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw fail("Unable to write to output file", "FILE_ERROR");
        }
    }

    @Override
    public String moveOutputFileToPlace(Path tempPath, String inputPath) {
        Path newPath = null;
        String extension = ".mp3";
        try {
            Path canonicalPath = Paths.get(inputPath)
                .toAbsolutePath().normalize()
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
            String baseName = FilenameUtils.getBaseName(canonicalPath.toString());
            Path parentDir = canonicalPath.getParent();
            newPath = parentDir.resolve(Paths.get(baseName + extension));
            int suffixCounter = 1;
            while (Files.exists(newPath)) {
                String newFileName = baseName + "-" + suffixCounter + extension;
                newPath = parentDir.resolve(newFileName);
                suffixCounter += 1;
            }
            Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (InvalidPathException | IOException e) {
            throw fail("Unable to move output file to destination", "FILE_ERROR");
        }
        return newPath.toString();
    }
}
// @@@SNIPEND
