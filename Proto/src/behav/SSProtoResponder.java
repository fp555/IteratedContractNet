/** Author: Fabio on 31/05/2016 */
package behav;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.states.MsgReceiver;

import java.util.Date;
import java.util.Random;

import static jade.proto.states.MsgReceiver.INFINITE;

public class SSProtoResponder extends FSMBehaviour {
    /** stato per ricevere le cfp */
    private static final String RECEIVE_CFP_STATE = "Receive-Cfp";
    /** stato per ricevere le request */
    private static final String RECEIVE_REQUEST_STATE = "Receive-Request";
    /** stato per ricevere le accept/reject proposal */
    private static final String RECEIVE_ARP_STATE = "Receive-Arp";
    /** stato finale per terminare il protocollo */
    private static final String FINAL = "Final";

    /** chiave del datastore per le risorse dell'agente (condivise tra tutte le sessioni responder) */
    static final String RISORSE_K = "__risorse-key";
    /** chiave del datastore per l'ultimo messaggio ricevuto */
    private final String LAST_K;

    //ctor
    public SSProtoResponder(Agent a, DataStore ds, String convID) {
        super(a);
        setDataStore(ds);
        LAST_K = convID; //esclusiva per questa sessione -> no sez critiche

        //registrazione transizioni
        registerTransition(RECEIVE_CFP_STATE, FINAL, ACLMessage.REFUSE);
        registerTransition(RECEIVE_CFP_STATE, RECEIVE_REQUEST_STATE, ACLMessage.PROPOSE);
        registerTransition(RECEIVE_REQUEST_STATE, RECEIVE_REQUEST_STATE, ACLMessage.PROPOSE,
                new String[]{RECEIVE_REQUEST_STATE});
        registerTransition(RECEIVE_REQUEST_STATE, FINAL, ACLMessage.REJECT_PROPOSAL);
        registerTransition(RECEIVE_REQUEST_STATE, RECEIVE_ARP_STATE, ACLMessage.AGREE);
        registerTransition(RECEIVE_REQUEST_STATE, FINAL, MsgReceiver.TIMEOUT_EXPIRED);
        registerDefaultTransition(RECEIVE_ARP_STATE, FINAL);

        //definizione stati
        Behaviour b = new OneShotBehaviour(myAgent) {
            private int ret;
            private ACLMessage cfp;

            @Override public void onStart() {
                super.onStart();
                cfp = (ACLMessage) getDataStore().get(LAST_K);
            }
            @Override public void action() {
                System.out.println(myAgent.getLocalName() + ": ho ricevuto una cfp da " + cfp.getSender().getLocalName());
                ACLMessage reply = cfp.createReply();
                int costo = new Random().nextInt(9) + 1; //generiamo il costo da proporre
                if(costo <= getRisorse()) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(Integer.toString(costo)); //settiamo il costo da proporre
                    System.out.println(myAgent.getLocalName() + ": propongo a " + cfp.getSender().getLocalName() +
                            " costo " + costo);
                }
                else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    System.out.println(myAgent.getLocalName() + ": rifiuto la cfp di " + cfp.getSender().getLocalName());
                }
                Date d = reply.getReplyByDate(); //sistemiamo la deadline
                if(d != null && d.getTime() > System.currentTimeMillis())
                    ((MsgReceiver) getState(RECEIVE_REQUEST_STATE)).setDeadline(d.getTime());
                ret = reply.getPerformative();
                myAgent.send(reply);
            }
            @Override public int onEnd() {
                return ret;
            }
        };
        b.setDataStore(getDataStore());
        registerFirstState(b, RECEIVE_CFP_STATE);

        b = new MsgReceiver(myAgent, MessageTemplate.and(MessageTemplate.or(MessageTemplate.MatchPerformative(
                ACLMessage.REQUEST), MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)),
                MessageTemplate.MatchConversationId(LAST_K)), INFINITE, getDataStore(), LAST_K) {
            private int ret;
            @Override protected void handleMessage(ACLMessage msg) {
                if(msg.getPerformative() == ACLMessage.REQUEST) {
                    System.out.println(myAgent.getLocalName() + ": ho ricevuto una request da " + msg.getSender().getLocalName());
                    ACLMessage reply = msg.createReply();
                    reply.setContent(msg.getContent());
                    if(Integer.parseInt(reply.getContent()) <= getRisorse()) {
                        reply.setPerformative(ACLMessage.AGREE);
                        System.out.println(myAgent.getLocalName() + ": accetto la request di " + msg.getSender().getLocalName());
                    }
                    else {
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(Integer.toString(getRisorse()));
                        System.out.println(myAgent.getLocalName() + ": ripropongo a " + msg.getSender().getLocalName() +
                                " costo " + reply.getContent());
                    }
                    Date d = reply.getReplyByDate(); //sistemiamo la deadline
                    if(d != null && d.getTime() > System.currentTimeMillis())
                        ((MsgReceiver) getState(RECEIVE_REQUEST_STATE)).setDeadline(d.getTime());
                    ret = reply.getPerformative();
                    myAgent.send(reply);
                }
                else ret = msg.getPerformative();
            }
            @Override public int onEnd() {
                return ret;
            }
        };
        registerState(b, RECEIVE_REQUEST_STATE);

        b = new MsgReceiver(myAgent, MessageTemplate.and(MessageTemplate.or(MessageTemplate.MatchPerformative(
                ACLMessage.ACCEPT_PROPOSAL), MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)),
                MessageTemplate.MatchConversationId(LAST_K)), INFINITE, getDataStore(), LAST_K) {
            @Override protected void handleMessage(ACLMessage msg) {
                super.handleMessage(msg);
                ACLMessage reply = msg.createReply();
                reply.setContent(msg.getContent());
                if(msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    setRisorse(getRisorse() - Integer.parseInt(msg.getContent())); //solo adesso consumiamo risorse
                    reply.setPerformative(ACLMessage.INFORM);
                    myAgent.send(reply);
                }
            }
        };
        registerState(b, RECEIVE_ARP_STATE);

        b = new OneShotBehaviour(myAgent) {
            @Override public void action() {
                System.out.println(myAgent.getLocalName() + ": ho terminato la richiesta");
            }
        };
        b.setDataStore(getDataStore());
        registerLastState(b, FINAL);
    } //fine costruttore

    private synchronized int getRisorse() {
        return (int) getDataStore().get(RISORSE_K);
    }
    private synchronized void setRisorse(int r) {
        getDataStore().put(RISORSE_K, r);
    }
}
