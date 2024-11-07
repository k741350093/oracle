package work.bigbrain;

import com.fasterxml.jackson.databind.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mybatis.spring.annotation.MapperScan;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@ComponentScan(basePackages = { "work.bigbrain", "work.bigbrain.api" })
@MapperScan("work.bigbrain.dao") // 扫描mapper
@EnableScheduling
public class OpenAPI2SpringBoot implements CommandLineRunner {
    private static final Logger logger = LogManager.getLogger(OpenAPI2SpringBoot.class);

    @Override
    public void run(String... arg0) throws Exception {
        if (arg0.length > 0 && arg0[0].equals("exitcode")) {
            throw new ExitException();
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.output.ansi.enabled", "ALWAYS");
        System.setProperty("file.encoding", "UTF-8");
        new SpringApplication(OpenAPI2SpringBoot.class).run(args);
    }

    static class ExitException extends RuntimeException implements ExitCodeGenerator {
        private static final long serialVersionUID = 1L;

        @Override
        public int getExitCode() {
            return 10;
        }

    }

    @Bean
    public WebMvcConfigurer webConfigurer() {
        return new WebMvcConfigurer() {
            /*
             * @Override
             * public void addCorsMappings(CorsRegistry registry) {
             * registry.addMapping("/**")
             * .allowedOrigins("*")
             * .allowedMethods("*")
             * .allowedHeaders("Content-Type");
             * }
             */
        };
    }

    @Bean
    public Module jsonNullableModule() {
        return new JsonNullableModule();
    }

}
