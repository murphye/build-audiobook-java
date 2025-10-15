// @@@SNIPSTART audiobook-project-java-Worker-app
package ttsworker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TTSWorkerApp {
    public static final String TASK_QUEUE = "tts-task-queue";
    private static final Logger logger = LoggerFactory.getLogger(TTSWorkerApp.class);

    public static void main(String[] args) {
        SpringApplication.run(TTSWorkerApp.class, args);
    }

    @Bean
    public TTSActivitiesImpl ttsActivities(OpenAiAudioSpeechModel speechModel) {
        return new TTSActivitiesImpl(speechModel);
    }
}
// @@@SNIPEND
