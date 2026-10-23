import com.nezurstandalone.gui.ContractorDashboard;
import com.nezurstandalone.gui.GrinderDashboard;
import com.nezurstandalone.gui.GrinderDashboard.*;
import com.nezurstandalone.gui.GuiDraw;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Builds real contractor snapshots and renders them through the shared production renderer. */
public final class ContractorDashboardTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static ContractorDashboard.Input base(){
        ContractorDashboard.Input i=new ContractorDashboard.Input();i.server="Pit Classic";i.gold=1647.39;
        i.uptime="12:30";i.completed=2;i.maxRetries=3;return i;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();GuiDraw.loadFont(args[1]);
        ContractorDashboard.Input idle=base();idle.phase="Finding contractor";
        Snapshot ordinary=ContractorDashboard.build(idle);
        check(ordinary.sections.isEmpty(),"No empty NPC, equipment or objective sections");
        check(ordinary.height()<140,"Idle HUD is compact");
        ContractorDashboard.Input active=base();active.active=true;active.badge="ACTIVE";active.phase="Executing contract";
        active.type="Kill streak";active.task="Reach a kill streak of 25.";active.current=4;active.target=25;active.seconds=238;active.vile=2;
        Snapshot live=ContractorDashboard.build(active);
        check(live.targetDetail.equals("4 / 25")&&live.health==4/25f,"Live streak drives progress bar");
        check(live.metrics.get(2).value.equals("3:58"),"Visible timer is contract time");
        check(live.sections.size()==1,"Active HUD only adds contract context");
        active.current=5;check(ContractorDashboard.build(active).targetDetail.equals("5 / 25"),"Progress updates without replacing contract");
        ContractorDashboard.Input choice=base();choice.phase="Verifying acceptance";choice.badge="VERIFYING";
        choice.choice="Collect 25 gold ingots from the ground.";choice.vile=2;choice.menu="Pick a contract!";choice.npcDistance=2.4;
        Snapshot selected=ContractorDashboard.build(choice);
        check(selected.sections.get(0).title.equals("Selected choice"),"Selected choice only shown while choosing");
        ContractorDashboard.Input restore=base();restore.phase="Restoring perks";restore.badge="RESTORING";
        restore.restoring=restore.perkLease=true;restore.perk="Restoring original loadout";
        Snapshot restoring=ContractorDashboard.build(restore);
        check(restoring.sections.size()==1&&restoring.sections.get(0).title.equals("Restoring perks"),"Restoration has focused context");
        active.detailed=true;Snapshot detailed=ContractorDashboard.build(active);
        check(detailed.height()>live.height(),"Detailed mode adds diagnostics dynamically");
        ContractorDashboard.Input longTask=base();longTask.active=true;longTask.type="No armor kills";longTask.current=3;longTask.target=10;longTask.seconds=64;
        longTask.task="Kill 10 players without wearing any armor and without returning to spawn.";
        Snapshot wrapped=ContractorDashboard.build(longTask);
        check(wrapped.sections.get(0).rows.size()>1,"Long objective wraps onto measured rows");
        ContractorDashboard.Input event=base();event.phase="Waiting for major event";event.badge="WAITING";event.event="Team Deathmatch";
        Snapshot waiting=ContractorDashboard.build(event);check(waiting.status.size()==1,"Event row only during event wait");
        Snapshot[] states={ordinary,live,selected,restoring,detailed,wrapped,waiting};
        BufferedImage sheet=new BufferedImage(900,690,BufferedImage.TYPE_INT_RGB);Graphics2D canvas=sheet.createGraphics();
        canvas.setColor(new Color(0x080d16));canvas.fillRect(0,0,900,690);
        for(int n=0;n<states.length;n++){
            Snapshot s=states[n];check(s.metrics.size()==(s.target.isEmpty()?3:4),"Metric cells adapt without empty streak tile "+n);
            check(s.metrics.get(0).label.equals("COMPLETED")&&s.metrics.get(0).value.equals("2"),"Contract completion count is the primary metric "+n);
            for(Metric metric:s.metrics)check(!metric.label.equals("STREAK"),"No separate current player streak metric "+n);
            BufferedImage image=new BufferedImage(284,s.height()+20,BufferedImage.TYPE_INT_ARGB);
            GuiDraw.g=image.createGraphics();GuiDraw.g.translate(10,10);GuiDraw.maxTextX=GuiDraw.maxTextY=0;
            GrinderDashboard.render(s,1);
            check(GuiDraw.maxTextX<=GrinderDashboard.WIDTH-8,"Text fits width "+n);
            check(GuiDraw.maxTextY<s.height()-2,"Text fits measured dynamic height "+n);
            check(s.height()<=235,"Context stays compact "+n);
            GuiDraw.g.dispose();ImageIO.write(image,"png",new File(dir,"contractor-"+n+".png"));
            canvas.drawImage(image,(n%3)*300,(n/3)*230,null);
        }
        canvas.dispose();ImageIO.write(sheet,"png",new File(dir,"contractor-overview.png"));
        System.out.println("Contractor dashboard: "+checks+" checks passed; idle="+ordinary.height()+" active="+live.height()+" px");
    }
}
