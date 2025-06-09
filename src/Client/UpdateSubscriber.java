package Client;

import com.rabbitmq.client.*;

public class UpdateSubscriber {
    private static final String EXCHANGE_NAME = "updates";

    public static void startListening(UpdateHandler handler) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        System.out.println(" [*] Waiting for updates...");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            handler.handleUpdate(message);
        };
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }

    public interface UpdateHandler {
        void handleUpdate(String message);
    }
} 