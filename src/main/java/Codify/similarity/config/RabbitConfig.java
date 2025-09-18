package Codify.similarity.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {


    //exchange로 topic 사용 -> parsingQueue, similarityQueue, clientQueue Topic으로 구분
    @Bean
    public TopicExchange codifyExchange() {
        return new TopicExchange("codifyExchange");
    }

    //parsing service -> similarity service 메시지 큐
    @Bean
    public Queue similarityQueue() {
        return QueueBuilder.durable("similarity.queue").build();
    }

    //parsing service -> similarity service
    @Bean
    public Binding similarityBinding() {
        return BindingBuilder
                .bind(similarityQueue())
                .to(codifyExchange())
                .with("parsing.complete");
    }

    //similarity service - client간의 메시지 큐
    @Bean
    public Queue clientQueue() {
        return QueueBuilder.durable("client.queue").build();
    }

    //similarity service -> client
    @Bean
    public Binding clientBinding() {
        return BindingBuilder
                .bind(clientQueue())
                .to(codifyExchange())
                .with("similarity.complete");
    }

    //message converter

    //메시지 전송
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());

        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        return factory;
    }

}