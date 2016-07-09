/** Author: Fabio on 24/05/2016 */
package agents;

import behav.SubInit;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class Iniziatore extends Agent {

    @Override protected void setup() {
        //setto le risorse
        int risorseMax = getArguments() != null ? Integer.parseInt(getArguments()[0].toString()) : new Random().nextInt(9) + 1;

        // registrazione al DF
        DFAgentDescription ad = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Iniziatore");
        sd.setType("ContractNet");
        ad.addServices(sd);

        //mandiamo una richiesta di sottoscrizione al df per il nostro protocollo
        ACLMessage subs = DFService.createSubscriptionMessage(this, getDefaultDF(), ad, null);

        //aggiungiamo behaviour all'agente
        doWait(1000); //giusto per sicurezza, aspetto che i partecipanti si registrano tutti
        addBehaviour(new SubInit(this, subs, risorseMax));
    }
}
