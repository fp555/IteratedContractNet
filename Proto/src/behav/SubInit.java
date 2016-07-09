/** Author: Gabriele on 06/06/2016 */
package behav;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.SubscriptionInitiator;

public class SubInit extends SubscriptionInitiator {
    private int maxRes;
    public SubInit(Agent myAgent, ACLMessage subs, int maxRes) {
        super(myAgent, subs);
        this.maxRes=maxRes;
    }
    @Override protected void handleInform (ACLMessage inform) {
        DFAgentDescription[] ads = null;
        try {
            ads = DFService.decodeNotification(inform.getContent());
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        myAgent.addBehaviour(new ProtoInitiator(myAgent, maxRes, ads));
    }
}


