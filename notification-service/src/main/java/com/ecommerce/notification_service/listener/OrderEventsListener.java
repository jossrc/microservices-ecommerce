package com.ecommerce.notification_service.listener;



import com.ecommerce.notification_service.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventsListener {

    private final JavaMailSender mailSender;

    @RabbitListener(queues = "notification-queue")
    public void handleOrderPlacedEvent(OrderPlacedEvent event) {
        log.info("Evento recibido en inventario para Orden: {}", event.orderNumber());
        try {

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("pedidos@ecommerce.com");
            message.setTo(event.email());
            message.setSubject("Orden Confirmada - " + event.orderNumber());
            message.setText("Hola!\n\n" +
                    "Tu pedido con número " + event.orderNumber() + " ha sido recibido exitosamente.\n" +
                    "Pronto recibirás más noticias sobre el envío.\n\n" +
                    "Gracias por comprar con nosotros!");
            mailSender.send(message);

            log.info("Correo enviado exitosamente para la orden: {}", event.orderNumber());
        }    catch (Exception e) {
            log.error("Error al enviar correo: {}", e.getMessage());
        }
    }

}
