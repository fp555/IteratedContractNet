/** Author: Fabio on 31/05/2016 */
package behav;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import static behav.SSProtoResponder.RISORSE_K;

public class ProtoResponder extends ParallelBehaviour {
    public ProtoResponder(Agent a, int endCond, int risorse) { //ctor
        super(a, endCond);
        getDataStore().put(RISORSE_K, risorse); // chiave del datastore per risorse (condivise) dell'agente

        //aggiungo sottobehaviour che riceve cfp
        addSubBehaviour(new CyclicBehaviour(myAgent) {
            @Override public void action() {
                //riceviamo le cfp e istanziamo un nuovo SSResponder
                ACLMessage newcfp = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
                if(newcfp != null) {
                    getParent().getDataStore().put(newcfp.getConversationId(), newcfp);
                    addSubBehaviour(new SSProtoResponder(myAgent, getParent().getDataStore(), newcfp.getConversationId()));
                }
                else {
                    //togliamo i behaviour che hanno già finito
                    for(Object b : getTerminatedChildren().toArray()) removeSubBehaviour((Behaviour) b);
                    block(); //si risveglierà appena riceviamo una cfp
                }
            }
        });
    }
}
