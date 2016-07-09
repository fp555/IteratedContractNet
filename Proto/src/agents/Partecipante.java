/** Author: Gabriele on 29/05/2016. */
package agents;

import behav.ProtoResponder;
import jade.core.Agent;
import jade.core.behaviours.ParallelBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.Random;

public class Partecipante extends Agent {

    @Override protected void setup() {
        //setto le risorse
        int risorse = getArguments() != null ? Integer.parseInt(getArguments()[0].toString()) : new Random().nextInt(9) + 1;

        // registrazione al DF
        DFAgentDescription ad = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Iniziatore");
        sd.setType("ContractNet");
        ad.addServices(sd);

        try { DFService.register(this, ad);}
        catch (FIPAException e) {
            e.printStackTrace();
        }

        //aggiungiamo behaviour all'agente
        addBehaviour(new ProtoResponder(this, ParallelBehaviour.WHEN_ALL, risorse));
    }
}
