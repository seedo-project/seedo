import { ProjectCard, type Project } from "@/components/project/project-card";

export function ProjectsPanel({ projects }: { projects: Project[] }) {
  if (projects.length === 0) {
    return (
      <div className="flex h-32 items-center justify-center rounded-md border border-border text-sm text-muted-foreground">
        아직 참여 중인 프로젝트가 없습니다.
      </div>
    );
  }
  return (
    <section className="flex flex-col gap-4">
      {projects.map((p) => (
        <ProjectCard key={p.id} project={p} />
      ))}
    </section>
  );
}
